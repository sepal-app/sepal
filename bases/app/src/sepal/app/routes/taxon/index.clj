(ns sepal.app.routes.taxon.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.taxon.export :as export]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.query-builder :as query-builder]
            [sepal.app.ui.table :as table]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [sepal.taxon.interface.permission :as taxon.perm]
            [sepal.taxon.interface.search]
            [zodiac.core :as z]))

(defn create-button []
  [:a {:class "spl-btn spl-btn--primary"
       :href (z/url-for taxon.routes/new)}
   "Create"])

(defn table-columns []
  [{:name "Name"
    :type :name
    :priority 1
    :cell (fn [t]
            [:a {:href (z/url-for taxon.routes/detail
                                  {:id (:taxon/id t)})
                 :class "spl-link"
                 :x-on:click.stop ""} ; Stop propagation so row click doesn't fire
             (taxon-name/render (:taxon/name t))])}
   {:name "Author"
    :type :text
    :priority 2
    :cell :taxon/author}
   {:name "Rank"
    :type :text
    :priority 3
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
    {:class "hover:bg-surface-alt cursor-pointer"
     :x-bind:class (str "selectedId === " id " ? 'bg-surface-alt' : ''")
     :x-on:click (str "selectRow(" id ", $el)")
     :hx-get panel-url
     :hx-trigger "panel-select"
     :hx-target (str "#" pages.list/panel-container-id)
     :hx-swap "innerHTML"
     :hx-push-url "false"}))

(defn index-rows
  "The <tr>s alone, for an infinite-scroll response. Same renderer as the
  initial load, so an appended row is built like one already present."
  [& {:keys [rows page page-size href total]}]
  (table/rows-only :columns (table-columns)
                   :rows rows
                   :row-attrs row-attrs
                   :next-page-url (table/next-page-url :href href
                                                       :page page
                                                       :page-size page-size
                                                       :total total)))

(defn table [& {:keys [rows page href page-size total]}]
  (table/card-table
    (table/table :columns (table-columns)
                 :rows rows
                 :row-attrs row-attrs
                 :next-page-url (table/next-page-url :href href
                                                     :page page
                                                     :page-size page-size
                                                     :total total))))
(defn- accessions-only-checkbox
  "Checkbox that toggles `accessions:>0` filter in the search query.
   Uses Alpine.js component from js/query-builder.ts"
  [q]
  (let [has-filter? (boolean (and q (re-find #"accessions:>0" q)))]
    [:label {:class "ml-4 flex items-center gap-2 text-sm cursor-pointer"
             :x-data (str "accessionsOnlyFilter('q', " has-filter? ")")}
     [:input {:type "checkbox"
              :class "spl-checkbox "
              :x-bind:checked "checked"
              :x-on:click.prevent "toggle()"}]
     [:span "Only taxa with accessions"]]))

(defn render [& {:keys [field-options viewer href page page-size rows search-query total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (table :href href
                                :page page
                                :page-size page-size
                                :rows rows
                                :total total)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for taxon.routes/export)
                           :options export/export-options)]
               :table-actions [:div {:class "flex items-center justify-between w-full"}
                               [:div {:class "flex items-center gap-2"}
                                (query-builder/search-field-with-builder
                                  :q search-query
                                  :fields field-options
                                  :placeholder "Search... (e.g., rank:species Quercus)")
                                (accessions-only-checkbox search-query)]
                               ;; The count sits with the search that changes it.
                               (table/row-count :loaded (min (* page page-size) total)
                                                :total total)
                               (ui.export/export-button)])

    :breadcrumbs ["Taxa"]
    :page-title-buttons (when (authz/user-has-permission? viewer taxon.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]])

(defn handler
  [& {:keys [::z/context headers query-params uri viewer]}]
  (let [{:keys [db]} context
        {:keys [page page-size q]} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ;; Parse search query
        ast (search.i/parse q)

        ;; Columns to select (including parent name for display)
        columns [[:t.id :id]
                 [:t.name :name]
                 [:t.rank :rank]
                 [:t.author :author]
                 [:t.parent_id :parent-id]
                 [:p.name :parent_name]
                 [:t.wfo_taxon_id :wfo_taxon_id]]

        ;; Base statement with parent join for display
        base-stmt {:select columns
                   :from [[:taxon :t]]
                   :left-join [[:taxon :p] [:= :p.id :t.parent_id]]}

        ;; Compile search query (adds WHERE clause and any filter joins)
        stmt (search.i/compile-query :taxon ast base-stmt)

        ;; Execute queries in parallel
        [rows total] (pcalls
                       #(db.i/execute! db (assoc stmt
                                                 :limit page-size
                                                 :offset offset
                                                 :order-by [[:t.name :asc]]))
                       #(db.i/count db stmt))]

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

      ;; Infinite scroll: the sentinel asks for the next page's rows alone and
      ;; swaps itself out for them.
      (some? (get query-params "rows"))
      (html/render-partial
        (index-rows :rows rows
                    :page page
                    :page-size page-size
                    :total total
                    :href (uri/uri-str {:path uri
                                        :query (uri/map->query-string
                                                 (cond-> {} (seq q) (assoc :q q)))})))

      :else
      (render :viewer viewer
              :field-options (search.i/field-options :taxon)
              :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string
                                           (cond-> {:page page}
                                             (seq q) (assoc :q q)))})
              :rows rows
              :page page
              :page-size page-size
              :search-query q
              :total total))))
