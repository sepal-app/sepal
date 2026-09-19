(ns sepal.app.routes.accession.index
  (:require [lambdaisland.uri :as uri]
            [sepal.accession.interface.permission :as accession.perm]
            [sepal.accession.interface.search]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.export :as export]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.combobox :as ui.combobox]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn create-button []
  (pages.list/create-button :href (z/url-for accession.routes/new)
                            :label "New accession"))

(defn- row-attrs [row]
  (let [id (:accession/id row)]
    (pages.list/row-attrs :id id
                          :panel-url (z/url-for accession.routes/panel {:id id}))))

(defn- provenance-label [row]
  (some-> (:accession/provenance-type row) (accession.form/enum-label-fn)))

(defn- stacked-summary
  "What the identifier cell shows below 640px, where the table collapses to a
  single column. Taxon and date are what tell two accessions apart in the
  field, so they are what survives.

  Markup rather than a joined string, so the name keeps the serif it carries
  in every other place this app prints one. A scientific name set in the body
  sans reads as a different kind of thing.

  Each item in the list is its own line: the narrow cell is a column flex
  container, so a separator between them would open a line rather than join
  anything."
  [row]
  (list (taxon-name/render (:taxon/name row))
        (when-let [received (:accession/date-received row)]
          [:span {:class "spl-stacked-line"} received])))

(defn table-columns []
  [{:name "Code"
    :type :identifier
    :priority 1
    :stacked stacked-summary
    :cell (fn [row] [:a {:href (z/url-for accession.routes/detail
                                          {:id (:accession/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:accession/code row)])}
   {:name "Taxon"
    :type :name
    :priority 1
    :cell (fn [row] [:a {:href (z/url-for taxon.routes/detail
                                          {:id (:taxon/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (taxon-name/render (:taxon/name row))])}
   {:name "Provenance"
    :type :text
    :priority 3
    :cell provenance-label}
   {:name "Received"
    :type :date
    :priority 2
    :cell :accession/date-received}])

(defn index-rows [& {:keys [rows page page-size href total]}]
  (table/rows-only :columns (table-columns)
                   :rows rows
                   :row-attrs row-attrs
                   :href href
                   :page page
                   :page-size page-size
                   :total total))

(defn table [& {:keys [rows page href page-size total search-query]}]
  (pages.list/card-table
    (table/table :columns (table-columns)
                 :rows rows
                 :row-attrs row-attrs
                 :href href
                 :page page
                 :page-size page-size
                 :total total
                 :empty-state (pages.list/empty-list
                                :noun "accessions"
                                :body "An accession is a batch of plant material acquired at one time from one
                              source."
                                :searching? (seq search-query)
                                :create-href (z/url-for accession.routes/new)
                                :create-label "New accession"))))

(defn render [& {:keys [field-options viewer href page page-size rows search-query taxon total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (table :href href
                                :page page
                                :page-size page-size
                                :rows rows
                                :total total
                                :search-query search-query)
                         ;; Export modal (hidden until triggered)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for accession.routes/export)
                           :options export/export-options)]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields field-options
                                :placeholder "Search... (e.g., taxon:Quercus provenance:wild)"
                                :page page
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs (cond-> []
                   taxon (conj [:a {:href (z/url-for taxon.routes/index)}
                                "Taxa"]
                               [:a {:href (z/url-for taxon.routes/detail {:id (:taxon/id taxon)})
                                    :class "italic"}
                                (:taxon/name taxon)])
                   :always (conj "Accessions"))
    :page-title-buttons (when (authz/user-has-permission? viewer accession.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]
   ;; Legacy params for backwards compatibility
   [:supplier-contact-id {:optional true} :int]
   [:taxon-id {:optional true} :int]])

(defn- normalize-query
  "Merge legacy filter params into q string for backwards compatibility."
  [{:keys [q taxon-id supplier-contact-id]}]
  (cond-> (or q "")
    taxon-id (str " taxon.id:" taxon-id)
    supplier-contact-id (str " supplier.id:" supplier-contact-id)))

(defn- extract-filter-value
  "Extract the value for a specific field from AST filters."
  [ast field-name]
  (->> (:filters ast)
       (filter #(= (:field %) field-name))
       first
       :value))

(defn handler [& {:keys [::z/context query-params uri viewer]}]
  (let [{:keys [db]} context
        {:keys [page page-size] :as decoded-params} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ;; Normalize legacy params into search query
        q (normalize-query decoded-params)
        ast (search.i/parse q)

        ;; Base statement with joins needed for display columns
        ;; (taxon name is shown in table)
        base-stmt {:select [:*]
                   :from [[:accession :a]]
                   :join [[:taxon :t] [:= :t.id :a.taxon_id]]}

        ;; Compile search query (adds WHERE clause)
        stmt (search.i/compile-query :accession ast base-stmt)

        ;; Execute queries
        total (db.i/count-bounded db stmt)
        rows (db.i/execute-bounded! db (assoc stmt
                                              :limit page-size
                                              :offset offset
                                              :order-by (concat (search.i/relevance-order :accession ast)
                                                                [[:a.code :asc]])))

        ;; Fetch taxon for breadcrumb if filtering by taxon.id
        taxon-id (some-> (extract-filter-value ast "taxon.id") parse-long)
        taxon (when taxon-id (taxon.i/get-by-id db taxon-id))]

    (cond
      ;; The combobox asks for its rows as markup, so what an option looks
      ;; like is decided here with the rest of the UI.
      (some? (get query-params "options"))
      (html/render-partial
        (ui.combobox/options-fragment
          :total total
          :items (for [row rows]
                   {:id (:accession/id row)
                    :text (format "%s (%s)"
                                  (:accession/code row)
                                  (:taxon/name row))
                    :content (ui.combobox/option-content
                               :icon (lucide/clipboard-list)
                               :title (:accession/code row)
                               :meta (taxon-name/render (:taxon/name row)))})))

      ;; Infinite scroll: the sentinel asks for the next page's rows alone and
      ;; swaps itself out for them, so this returns <tr>s with no page around
      ;; them. Same renderer as the initial load, so an appended row is built
      ;; exactly like one that was already there.
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
              :field-options (search.i/field-options :accession)
              :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string
                                           (cond-> {:page page}
                                             (seq q) (assoc :q q)))})
              :rows rows
              :page page
              :page-size page-size
              :search-query q
              :taxon taxon
              :total total))))
