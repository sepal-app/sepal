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
            :id "q"
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
     ;; TODO: publish an htmx event and use something like "closest" so we
     ;; don't step on other elements
     :onclick "document.getElementById('q').value = null; this.form.submit()"
     ;; :hx-trigger "clicked"
     }
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
                                      {:id (:id t)})
                       :class "spl-link"}
                   (:name t)])}
   {:name "Author"
    :cell :author}
   {:name "Rank"
    :cell :rank}
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
        org (:organization context)
        {:keys [_accessions-only page page-size q] :as params} (decode-params Params query-params)
        offset (* page-size (- page 1))
        tsquery (when q [:to_tsquery (str q ":*")])
        t-name-tsvector [:to_tsvector [:raw "'english'"] :t.name]
        wfo-n-name-tsvector [:to_tsvector [:raw "'english'"] :wfo_n.scientific_name]
        stmt {:with [[:wfo_taxon {:select [:wfo_t.id
                                           [:wfo_n.scientific_name :name]
                                           [:wfo_n.rank :rank]
                                           [:wfo_n.authorship :author]
                                           [:wfo_t.parent_id]
                                           [:wfo_n.id :wfo_plantlist_name_id]
                                           [(if (seq q)
                                              [:ts_rank_cd wfo-n-name-tsvector tsquery [:cast 1 :integer]]
                                              1.0)
                                            :search-rank]]
                                  :from [[:wfo_plantlist_current.name :wfo_n]]
                                  :join-by [:left [[:wfo_plantlist_current.taxon :wfo_t]
                                                   [:= :wfo_t.name_id :wfo_n.id]]]
                                  :where [:and
                                          :true
                                          (if (seq q)
                                            [(keyword "@@") wfo-n-name-tsvector tsquery]
                                            :true)]}]
                     [:org_taxon {:select [[[:cast :t.id :text] :id]
                                           :t.name
                                           [[:cast :t.rank :text] :rank]
                                           :t.author
                                           [[:cast :t.parent_id :text] :parent_id]
                                           :t.wfo_plantlist_name_id
                                           [(if (seq q)
                                              [:ts_rank_cd t-name-tsvector tsquery [:cast 1 :integer]]
                                              1.0)
                                            :search-rank]]
                                  :from [[:public.taxon :t]]
                                  :where [:and
                                          [:= :t.organization_id (:organization/id org)]
                                          (if (seq q)
                                            [(keyword "@@") t-name-tsvector tsquery]
                                            :true)]}]
                     [:all_taxon {:union-all [{:select :*
                                               :from :wfo_taxon}
                                              {:select :*
                                               :from :org_taxon}]}]]
              :select :*
              :from :all_taxon}
        ;;
        ;; TODO: Do the queries in parallel for faster response
        total (db.i/count db (-> stmt
                                 (dissoc :order-by)
                                 (assoc :select 1)))
        rows (->> (db.i/execute! db
                                 (assoc stmt
                                        :limit page-size
                                        :offset offset
                                        :order-by [[:search-rank :desc] [:name :asc]])))]

    (tap> (str "rows: " rows))

    (cond
      (= (get headers "accept") "application/json")
      ;; TODO: Use Taxon json transformer
      (json/json-response (for [taxon rows]
                            {:text (:name taxon)
                             :name (:name taxon)
                             :id (:id taxon)
                             :author (:author taxon)
                             :parentId (:parent-id taxon)}))

      :else
      (render :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :org org
              :router router
              :rows rows
              :page page
              :page-size page-size
              :total total))))
