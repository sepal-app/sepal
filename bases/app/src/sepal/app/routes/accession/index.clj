(ns sepal.app.routes.accession.index
  (:require [sepal.accession.interface.permission :as accession.perm]
            [sepal.accession.interface.search]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.app.authorization :as authz]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.list-query :as list-query]
            [sepal.app.list-view :as list-view]
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
            [sepal.i18n.interface :refer [tr]]
            [sepal.search.interface :as search.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn create-button []
  (pages.list/create-button :href (z/url-for accession.routes/new)
                            :label (tr "New accession")))

(defn- row-attrs [row]
  (let [id (:accession/id row)]
    (pages.list/row-attrs :id id
                          :panel-url (z/url-for accession.routes/panel {:id id}))))

(defn- provenance-label [row]
  (accession.form/enum-label accession.spec/provenance-type-labels (:accession/provenance-type row)))

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
          [:span {:class "spl-stacked-line"} (datetime/format-date received)])))

(defn- supplier-query [stmt]
  (-> stmt
      (list-query/add-left-join [:contact :sc] [:= :sc.id :a.supplier_contact_id])
      (list-query/add-select [:sc.name :accession__supplier_name])))

(defn table-columns [timezone]
  (into
    [{:name (tr "Code")
      :key :code
      :type :identifier
      :priority 1
      :sort [:a.code]
      :stacked stacked-summary
      :cell (fn [row] [:a {:href (z/url-for accession.routes/detail
                                            {:id (:accession/id row)})
                           :class "spl-link"
                           :x-on:click.stop ""}
                       (:accession/code row)])}
     {:name (tr "Taxon")
      :key :taxon
      :type :name
      :priority 1
      :sort [:t.name]
      :cell (fn [row] [:a {:href (z/url-for taxon.routes/detail
                                            {:id (:taxon/id row)})
                           :class "spl-link"
                           :x-on:click.stop ""}
                       (taxon-name/render (:taxon/name row))])}
     {:name (tr "Provenance")
      :key :provenance
      :type :text
      :priority 3
      :sort [:a.provenance_type]
      :cell provenance-label}
     {:name (tr "Received")
      :key :received
      :type :date
      :priority 2
      :sort [:a.date_received]
      :cell :accession/date-received}
     {:name (tr "Supplier")
      :key :supplier
      :type :text
      :priority 3
      :hidden? true
      :query supplier-query
      :sort [[:lower :sc.name]]
      :cell :accession/supplier-name}
     {:name (tr "Accessioned")
      :key :accessioned
      :type :date
      :priority 3
      :hidden? true
      :sort [:a.date_accessioned]
      :cell :accession/date-accessioned}
     {:name (tr "Received as")
      :key :received-as
      :type :text
      :priority 3
      :hidden? true
      :sort [:a.received_type]
      :cell #(some-> (:accession/received-type %) keyword accession.spec/received-type-labels tr)}
     {:name (tr "Quantity received")
      :key :quantity-received
      :type :number
      :priority 3
      :hidden? true
      :sort [:a.quantity_received]
      :cell :accession/quantity-received}]
    (table/timestamp-columns :created [:a.created_at :accession/created-at]
                             :updated [:a.updated_at :accession/updated-at]
                             :timezone timezone)))

(defn index-rows [& {:keys [rows page page-size href total table-opts]}]
  (table/rows-only (merge table-opts
                          {:rows rows
                           :row-attrs row-attrs
                           :href href
                           :page page
                           :page-size page-size
                           :total total})))

(defn table [& {:keys [rows page href page-size total search-query table-opts]}]
  (pages.list/card-table
    (table/table (merge table-opts
                        {:rows rows
                         :row-attrs row-attrs
                         :href href
                         :page page
                         :page-size page-size
                         :total total
                         :empty-state (pages.list/empty-list
                                        :title (tr "No accessions yet")
                                        :body (tr "An accession is a batch of plant material acquired at one time from one source.")
                                        :searching? (seq search-query)
                                        :create-href (z/url-for accession.routes/new)
                                        :create-label (tr "New accession"))}))))

(defn render [& {:keys [field-options viewer href page page-size rows search-query table-opts taxon total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (table :href href
                                :page page
                                :page-size page-size
                                :rows rows
                                :total total
                                :search-query search-query
                                :table-opts table-opts)
                         ;; Export modal (hidden until triggered)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for accession.routes/export)
                           :options export/export-options)]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields field-options
                                ;; i18n: Keep "taxon:", "provenance:" and "wild" in English; they are search syntax
                                :placeholder (tr "Search... (e.g., taxon:Quercus provenance:wild)")
                                :page page
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs (cond-> []
                   taxon (conj [:a {:href (z/url-for taxon.routes/index)}
                                (tr "Taxa")]
                               [:a {:href (z/url-for taxon.routes/detail {:id (:taxon/id taxon)})
                                    :class "italic"}
                                (:taxon/name taxon)])
                   :always (conj (tr "Accessions")))
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

(defn handler [& {:keys [::z/context query-params uri viewer] :as request}]
  (let [{:keys [db timezone]} context
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
        stmt (search.i/compile-query :accession ast base-stmt {:timezone timezone})

        ;; Execute queries
        total (db.i/count-bounded db stmt)
        view (list-view/resolve request :accession (table-columns timezone))
        rows (db.i/execute-bounded! db (-> stmt
                                           (list-query/with-columns (:columns view) (:sort view))
                                           (assoc :limit page-size
                                                  :offset offset
                                                  :order-by (list-query/order-by
                                                              (:sort view)
                                                              {:relevance (search.i/relevance-order :accession ast)
                                                               :default [[:a.code :asc]]
                                                               :tiebreak [:a.id :asc]}))))
        table-opts (list-view/table-opts view uri q)

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
                    :table-opts table-opts
                    :href (list-view/href view uri q)))

      :else
      (list-view/respond
        view
        (render :viewer viewer
                :field-options (search.i/field-options :accession)
                :href (list-view/href view uri q :page page)
                :rows rows
                :page page
                :page-size page-size
                :search-query q
                :table-opts table-opts
                :taxon taxon
                :total total)
        uri q))))
