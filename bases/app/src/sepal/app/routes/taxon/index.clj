(ns sepal.app.routes.taxon.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(defn create-button [& {:keys []}]
  [:a {:class "btn btn-primary"
       :href (z/url-for taxon.routes/new)}
   "Create"])

(defn table-columns []
  [{:name "Name"
    :cell (fn [t]
            [:a {:href (z/url-for taxon.routes/detail
                                  {:id (:taxon/id t)})
                 :class "spl-link"
                 :x-on:click.stop ""} ; Stop propagation so row click doesn't fire
             (:taxon/name t)])}
   {:name "Author"
    :cell :taxon/author}
   {:name "Rank"
    :cell :taxon/rank}
   {:name "Parent"
    :cell (fn [t]
            (when (:taxon/parent-id t)
              [:a {:href (z/url-for taxon.routes/detail
                                    {:id (:taxon/parent-id t)})
                   :class "spl-link"
                   :x-on:click.stop ""} ; Stop propagation
               (:taxon/parent-name t)]))}])

(defn- row-attrs
  "Generate attributes for a table row to enable panel preview."
  [row]
  (let [id (:taxon/id row)
        panel-url (z/url-for taxon.routes/panel {:id id})]
    {:class "hover:bg-base-200 cursor-pointer"
     :x-bind:class (str "selectedId === " id " ? 'bg-base-200' : ''")
     :x-on:click (str "selectedId = " id "; panelOpen = true")
     :hx-get panel-url
     :hx-target (str "#" pages.list/panel-container-id)
     :hx-swap "innerHTML"
     :hx-push-url "false"}))

(defn table [& {:keys [rows page href page-size total]}]
  [:div {:class "w-full"}
   (table/card-table
     (table/table :columns (table-columns)
                  :rows rows
                  :row-attrs row-attrs)
     (table/paginator :current-page page
                      :href href
                      :page-size page-size
                      :total total))])

(defn render [& {:keys [href page page-size rows total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content (table :href href
                               :page page
                               :page-size page-size
                               :rows rows
                               :total total)
               :table-actions [(pages.list/search-field (-> href uri/query-map :q))
                               [:label {:class "ml-8"}
                                "Only taxa with accessions"
                                ;; TODO: Pass this value in and set it here so
                                ;; that it matches the url for form submissions
                                [:input {:type "checkbox"
                                         :name "accessions-only"
                                         :value "1"
                                         :class "ml-4"}]]])

    :breadcrumbs ["Taxa"]
    :page-title-buttons (create-button)))

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
        columns (cond-> [[:t.id :id]
                         [:t.name :name]
                         [:t.rank :rank]
                         [:t.author :author]
                         [:t.parent_id :parent-id]
                         [:p.name :parent_name]
                         [:t.wfo_taxon_id :wfo_taxon_id]]
                  (seq q) (conj [:fts.rank :search-rank]))
        stmt {:select columns
              :from [[:taxon :t]]
              :join-by (cond-> [:left [[:taxon :p]
                                       [:= :p.id :t.parent_id]]]
                         (seq q)
                         (conj :inner [[:taxon_fts :fts]
                                       [:= :fts.rowid :t.id]])
                         accessions-only
                         (conj :inner [[:accession :a]
                                       [:= :a.taxon_id :t.id]]))
              :where (if (seq q)
                       [:match :taxon_fts (str q "*")]
                       :true)}
        [rows total] (pcalls
                       #(->> (db.i/execute! db (assoc stmt
                                                      :limit page-size
                                                      :offset offset
                                                      :order-by [[:t.name :asc]])))
                       #(db.i/count db (assoc stmt :select 1)))]
    (cond
      ;; We return JSON for autocomplete fields
      (= (get headers "accept") "application/json")
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
