(ns sepal.app.routes.taxon.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(defn search-field [q]
  [:div {:class "flex flex-row"}
   [:input {:name "q"
            :id "q"
            :class "input input-md w-fill max-w-xs bg-white w-96"
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

(defn create-button [& {:keys []}]
  [:a {:class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                         "border" "border-transparent" "bg-green-700" "px-4" "py-2"
                         "text-sm" "font-medium" "text-white" "shadow-sm" "hover:bg-green-700"
                         "focus:outline-none" "focus:ring-2" "focus:ring-grenn-500"
                         "focus:ring-offset-2" "sm:w-auto")
       :href (z/url-for taxon.routes/new)}
   "Create"])

(defn table-columns []
  [{:name "Name"
    :cell (fn [t]
            [:a {:href (z/url-for taxon.routes/detail
                                  {:id (:taxon/id t)})
                 :class "spl-link"}
             (:taxon/name t)])}
   {:name "Author"
    :cell :taxon/author}
   {:name "Rank"
    :cell :taxon/rank}
   {:name "Parent"
    :cell (fn [t] [:a {:href (z/url-for taxon.routes/detail
                                        {:id (:taxon/parent-id t)})
                       :class "spl-link"}
                   (:taxon/parent-name t)])}])

(defn table [& {:keys [rows page href page-size total]}]
  [:div {:class "w-full"}
   (table/card-table
     (table/table :columns (table-columns)
                  :rows rows)
     (table/paginator :current-page page
                      :href href
                      :page-size page-size
                      :total total))])

(defn render [& {:keys [href page page-size rows total]}]
  (pages.list/render :content (table :href href
                                     :page page
                                     :page-size page-size
                                     :rows rows
                                     :total total)
                     :page-title "Taxa"
                     :page-title-buttons (create-button)
                     :table-actions [(search-field (-> href uri/query-map :q))
                                     [:label {:class "ml-8"}
                                      "Only taxa with accessions"
                                      ;; TODO: Pass this value in and set it here so
                                      ;; that it matches the url for form submissions
                                      [:input {:type "checkbox"
                                               :name "accessions-only"
                                               :value "1"
                                               :class "ml-4"}]]]))

(def Params
  [:map
   [:accessions-only {:default false
                      :decode/string #(= "1" %)}
    :boolean]
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]])

(defn handler
  [& {:keys [::z/context headers query-params uri]}]
  (let [{:keys [db]} context
        {:keys [accessions-only page page-size q]} (params/decode Params query-params)
        offset (* page-size (- page 1))
        stmt {:select [[:t.id :id]
                       [:t.name :name]
                       [:t.rank :rank]
                       [:t.author :author]
                       [:t.parent_id :parent-id]
                       [:p.name :parent_name]
                       [:t.wfo_taxon_id_2023_12 :wfo_taxon_id_2023_12]
                       [(if (seq q)
                          [:similarity :t.name q]
                          1.0)
                        :search-rank]]
              :from [[:public.taxon :t]]
              :join-by (cond-> [:left [[:public.taxon :p]
                                       [:= :p.id :t.parent_id]]]
                         accessions-only
                         (conj :inner [[:public.accession :a]
                                       [:= :a.taxon_id :t.id]]))
              :where (if (seq q)
                       [:%> :t.name q]
                       :true)}
        ;; TODO: Do the queries in parallel for faster response
        total (db.i/count db (assoc stmt :select 1))
        ;; ;; TODO: Can we use jdbc datafy/nav to eager load the parent
        rows (->> (db.i/execute! db (assoc stmt
                                           :limit page-size
                                           :offset offset
                                           :order-by [[:search-rank :desc] [:t.name :asc]])))]

    #_(tap> (str "rows: " rows))

    (cond
      (= (get headers "accept") "application/json")
      ;; TODO: Use Taxon json transformer
      (json/json-response (for [taxon rows]
                            {:text (:taxon/name taxon)
                             :name (:taxon/name taxon)
                             :id (:taxon/id taxon)
                             :rank (:taxon/rank taxon)
                             :author (:taxon/author taxon)
                             :parentId (:taxon/parent-id taxon)
                             :parentName (:taxon/parent-name taxon)}))

      :else
      (render :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :rows rows
              :page page
              :page-size page-size
              :total total))))
