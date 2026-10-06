(ns sepal.app.routes.location.index
  (:require [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.list-query :as list-query]
            [sepal.app.list-view :as list-view]
            [sepal.app.params :as params]
            [sepal.app.routes.location.export :as export]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.combobox :as ui.combobox]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.location-path :as location-path]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr trc]]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.permission :as location.perm]
            [sepal.location.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn create-button []
  (pages.list/create-button :href (z/url-for location.routes/new)))

(defn row-attrs [row]
  (let [id (:location/id row)]
    (pages.list/row-attrs :id id
                          :panel-url (z/url-for location.routes/panel {:id id}))))

(defn- stacked-summary
  "What the name cell shows below 640px, where the table collapses to a single
  column."
  [l]
  (table/summary (:location/code l) (:location/description l)))

(defn table-columns [timezone]
  (into
    [{:name (tr "Name")
      :key :name
      :type :text
      :priority 1
      :sort [[:lower :l.name]]
      :stacked stacked-summary
      :cell (fn [l] [:a {:href (z/url-for location.routes/detail
                                          {:id (:location/id l)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:location/name l)])}
     {:name (tr "Code")
      :key :code
      :type :identifier
      :priority 2
      :sort [:l.code]
      :cell :location/code}
     {:name (trc "location" "Parent")
      :key :parent
      :type :text
      :priority 4
      :cell (fn [l] (some-> (:location/parent-path l) seq location-path/markup))}
     {:name (tr "Description")
      :key :description
      :type :text
      :priority 3
      :sort [[:lower :l.description]]
      :cell :location/description}
     {:name (tr "Status")
      :key :status
      :type :text
      :priority 3
      :hidden? true
      :sort [:l.status]
      :cell #(case (:location/status %)
               "active" (tr "Active")
               "archived" (tr "Archived")
               (:location/status %))}
     {:name (trc "navigation" "Material")
      :key :material
      :type :number
      :priority 3
      :hidden? true
      :query #(list-query/add-select % [{:select [[[:count :*]]]
                                         :from [[:material :lm]]
                                         :where [:= :lm.location_id :l.id]}
                                        :location__material_count])
      :sort [:location__material_count]
      :cell :location/material-count}]
    (table/timestamp-columns :created [:l.created_at :location/created-at]
                             :updated [:l.updated_at :location/updated-at]
                             :timezone timezone)))

(defn index-rows
  "The <tr>s alone, for an infinite-scroll response. Same renderer as the
  initial load, so an appended row is built like one already present."
  [& {:keys [rows page-num page-size href total table-opts]}]
  (table/rows-only (merge table-opts
                          {:rows rows
                           :row-attrs row-attrs
                           :href href
                           :page page-num
                           :page-size page-size
                           :total total})))

(defn table [& {:keys [rows page-num href page-size total search-query table-opts]}]
  (pages.list/card-table
    (table/table (merge table-opts
                        {:rows rows
                         :row-attrs row-attrs
                         :href href
                         :page page-num
                         :page-size page-size
                         :total total
                         :empty-state (pages.list/empty-list
                                        :title (tr "No locations yet")
                                        :body (tr "The beds, houses and stores that material lives in.")
                                        :searching? (seq search-query)
                                        :create-href (z/url-for location.routes/new))}))))

(defn render [& {:keys [field-options viewer href page-num page-size rows search-query table-opts total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (table :href href
                                :page-num page-num
                                :page-size page-size
                                :rows rows
                                :total total
                                :search-query search-query
                                :table-opts table-opts)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for location.routes/export)
                           :options export/export-options)]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields field-options
                                ;; i18n: Keep "taxon:" in English; it is search syntax
                                :placeholder (tr "Search... (e.g., taxon:Quercus)")
                                :page page-num
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs [(tr "Locations")]
    :page-title-buttons (when (authz/user-has-permission? viewer location.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default default-page-size} :int]
   [:q :string]
   [:exclude {:optional true} :int]])

(defn handler [& {:keys [::z/context query-params uri viewer] :as request}]
  (let [{:keys [db timezone]} context
        {:keys [page page-size q exclude]} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ;; Parse search query
        ast (search.i/parse q)

        ;; Base statement
        base-stmt {:select [:l.*]
                   :from [[:location :l]]}

        ;; The pickers ask for JSON. They exist to file material somewhere, and
        ;; an archived location takes none, so they never offer one whatever
        ;; the query says.
        picker? (some? (get query-params "options"))

        ;; The list hides them too, until asked. `archived:true` lists the
        ;; retired ones and `archived:false` the rest; either way the reader
        ;; has said what they want and this leaves the query alone.
        asked-about-archived? (boolean (some #(= "archived" (:field %))
                                             (:filters ast)))

        ;; Compile search query (adds WHERE clause and joins)
        stmt (cond-> (search.i/compile-query :location ast base-stmt {:timezone timezone})
               (or picker? (not asked-about-archived?))
               (update :where #(let [active [:= :l.status "active"]]
                                 (if % [:and % active] active)))

               ;; Choosing a parent while editing: a location can't sit inside
               ;; itself or anything below it, so neither is offered.
               exclude
               (update :where #(let [clause [:not-in :l.id (location.i/subtree [:= :l.id exclude])]]
                                 (if % [:and % clause] clause))))

        ;; Execute queries
        total (db.i/count-bounded db stmt)
        view (list-view/resolve request :location (table-columns timezone))
        rows (db.i/execute-bounded! db (-> stmt
                                           (list-query/with-columns (:columns view) (:sort view))
                                           (assoc :limit page-size
                                                  :offset offset
                                                  :order-by (list-query/order-by
                                                              (:sort view)
                                                              {:relevance (search.i/relevance-order :location ast)
                                                               :default [[:l.name :asc]]
                                                               :tiebreak [:l.id :asc]}))))
        ;; Each row's ancestors, from one query for the page: the list's
        ;; Parent column and the picker's meta line both show them.
        rows (let [paths (location.i/paths db (map :location/id rows))]
               (mapv #(assoc % :location/parent-path (butlast (get paths (:location/id %))))
                     rows))
        table-opts (list-view/table-opts view uri q)]

    (cond
      ;; The combobox asks for its rows as markup, so what an option looks like
      ;; is decided here with the rest of the UI rather than assembled from
      ;; JSON in the browser.
      (some? (get query-params "options"))
      (html/render-partial
        (ui.combobox/options-fragment
          :total total
          :items (for [location rows
                       :let [ancestors (:location/parent-path location)]]
                   {:id (:location/id location)
                      ;; What the field shows once it is chosen: one line, plain.
                    :text (format "%s (%s)"
                                  (:location/code location)
                                  (:location/name location))
                    :content (ui.combobox/option-content
                               :icon (lucide/map-pin)
                               :title (:location/name location)
                                 ;; The parent path tells two Row 3s apart.
                               :meta (cond-> (:location/code location)
                                       (seq ancestors)
                                       (str " · " (location-path/text ancestors))))})))

      ;; Infinite scroll: the sentinel asks for the next page's rows alone and
      ;; swaps itself out for them.
      (some? (get query-params "rows"))
      (html/render-partial
        (index-rows :rows rows
                    :page-num page
                    :page-size page-size
                    :total total
                    :table-opts table-opts
                    :href (list-view/href view uri q)))

      :else
      (list-view/respond
        view
        (render :viewer viewer
                :field-options (search.i/field-options :location)
                :href (list-view/href view uri q :page page)
                :rows rows
                :page-num page
                :page-size page-size
                :search-query q
                :table-opts table-opts
                :total total)
        uri q))))
