(ns sepal.app.routes.taxon.index
  (:require [lambdaisland.uri :as uri]
            [malli.core :as m]
            [malli.transform :as mt]
            [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]))

(defn search-field [q]
  [:div {:class "flex flex-row"}
   [:input {:name "q"
            :class "spl-input w-96"
            :type "search"
            :value q
            :placeholder "Search..."}]
   [:button
    {:type "button",
     :class (html/attr "inline-flex" "items-center" "mx-2" "px-2.5" "py-1.5" "border"
                       "border-gray-300" "shadow-sm" "text-xs" "font-medium" "rounded"
                       "text-gray-700" "bg-white" "hover:bg-gray-50" "focus:outline-none"
                       "focus:ring-2" "focus:ring-offset-2" "focus:ring-indigo-500")
     :onclick "document.getElementById('q').value = null; this.form.submit()"}
    (heroicons/outline-x :size 20)]])

(defn create-button [& {:keys [org router]}]
  [:a {:class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                         "border" "border-transparent" "bg-green-700" "px-4" "py-2"
                         "text-sm" "font-medium" "text-white" "shadow-sm" "hover:bg-green-700"
                         "focus:outline-none" "focus:ring-2" "focus:ring-grenn-500"
                         "focus:ring-offset-2" "sm:w-auto")
       :href (url-for router :org/taxa-new {:org-id (:organization/id org)})}
   "Create"])

(defn table-columns [router]
  [{:name "Name"
    :cell (fn [t] [:a {:href (url-for router
                                      :taxon/detail
                                      {:id (:taxon/id t)})
                       :class "spl-link"}
                   (:taxon/name t)])}
   {:name "Author"
    :cell :taxon/author}
   {:name "Rank"
    :cell :taxon/rank}
   #_{:name "Parent"
      :cell (fn [t] [:a {:href (url-for router
                                        :taxon/detail
                                        {:id (:taxon/id t)})
                         :class "spl-link"}
                     (:taxon/name t)])}])

(defn table [& {:keys [rows page href page-size router total]}]
  [:div {:class "w-full"}
   (table/card-table
    (table/table :columns (table-columns router)
                 :rows rows)
    (table/paginator :current-page page
                     :href href
                     :page-size page-size
                     :total total))])

(defn render [& {:keys [href org page page-size router rows total]}]
  (-> (pages.list/render :content (table :href href
                                         :page page
                                         :page-size page-size
                                         :router router
                                         :rows rows
                                         :total total)
                         :page-title "Taxa"
                         :page-title-buttons (create-button :router router
                                                            :org org)
                         :table-actions (search-field (-> href uri/query-map :q))
                         :router router)
      (html/render-html)))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]])

(def params-transformer (mt/transformer
                         (mt/key-transformer {:decode keyword})
                         mt/strip-extra-keys-transformer
                         mt/default-value-transformer
                         mt/string-transformer))

(defn decode-params [schema params]
  (m/decode schema params params-transformer))

(defn handler
  [& {:keys [context headers query-params ::r/router uri]}]
  (let [{:keys [db]} context
        org (:current-organization context)
        {:keys [page page-size q] :as params} (decode-params Params query-params)
        offset (* page-size (- page 1))
        stmt {:select [:t.*]
              :from [[:public.taxon :t]]
              :where [:and
                      [:= :organization_id (:organization/id org)]
                      (if q
                        [:ilike :name (format "%%%s%%" q)]
                        :true)]}
        total (db.i/count db stmt)
        ;; TODO: Can we use jdbc datafy/nav to eager load the parent
        ;;
        ;; TODO: Use taxon.i/find or something to coerce to Taxonkj
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      :order-by [:name]))]

    (tap> (str "rows: " rows))

    (cond
      (= (get headers "accept") "application/json")
      ;; TODO: Use Taxon json transformer
      (json/json-response (for [taxon rows]
                            {:text (:taxon/name taxon)
                             :name (:taxon/name taxon)
                             :id (:taxon/id taxon)
                             :author (:taxon/author taxon)}))

      :else
      (render :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :org org
              :router router
              :rows rows
              :page page
              :page-size page-size
              :total total))))
