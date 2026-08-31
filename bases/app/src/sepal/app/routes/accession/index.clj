(ns sepal.app.routes.accession.index
  (:require [clojure.string :as str]
            [lambdaisland.uri :as uri]
            [sepal.accession.interface.permission :as accession.perm]
            [sepal.accession.interface.search]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.export :as export]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.query-builder :as query-builder]
            [sepal.app.ui.table :as table]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn create-button []
  [:a {:class "spl-btn spl-btn--primary"
       :href (z/url-for accession.routes/new)}
   "New accession"])

(defn- row-attrs
  "Generate attributes for a table row to enable panel preview.

  The click handler is an enhancement — the anchor inside the Code cell is the
  real affordance and is what a keyboard user reaches."
  [row]
  (let [id (:accession/id row)
        panel-url (z/url-for accession.routes/panel {:id id})]
    {:class "spl-row"
     :x-bind:class (str "selectedId === " id " ? 'spl-row--selected' : ''")
     :x-on:click (str "selectRow(" id ", $el)")
     :hx-get panel-url
     :hx-trigger "panel-select"
     :hx-target (str "#" pages.list/panel-container-id)
     :hx-swap "innerHTML"
     :hx-push-url "false"}))

(defn- provenance-label [row]
  (some-> (:accession/provenance-type row) (accession.form/enum-label-fn)))

(defn- stacked-summary
  "What the identifier cell shows below 640px, where the table collapses to a
  single column. Taxon and date are what tell two accessions apart in the
  field, so they are what survives."
  [row]
  (->> [(:taxon/name row)
        (:accession/date-received row)]
       (remove str/blank?)
       (str/join " · ")))

(defn table-columns []
  [{:name "Code"
    :type :identifier
    :priority 1
    :attrs (fn [row] {:data-stacked (stacked-summary row)})
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

(defn render [& {:keys [field-options viewer href page page-size rows search-query taxon total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (table :href href
                                :page page
                                :page-size page-size
                                :rows rows
                                :total total)
                         ;; Export modal (hidden until triggered)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for accession.routes/export)
                           :options export/export-options)]
               :table-actions [:div {:class "flex items-center justify-between w-full"}
                               (query-builder/search-field-with-builder
                                 :q search-query
                                 :fields field-options
                                 :placeholder "Search... (e.g., taxon:Quercus provenance:wild)")
                               ;; The count sits with the search that changes it.
                               (table/row-count :loaded (min (* page page-size) total)
                                                :total total)
                               (ui.export/export-button)])
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

(defn handler [& {:keys [::z/context headers query-params uri viewer]}]
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
        total (db.i/count db stmt)
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      :order-by [:a.code]))

        ;; Fetch taxon for breadcrumb if filtering by taxon.id
        taxon-id (some-> (extract-filter-value ast "taxon.id") parse-long)
        taxon (when taxon-id (taxon.i/get-by-id db taxon-id))]

    (cond
      (= (get headers "accept") "application/json")
      (json/json-response (for [row rows]
                            {:text (format "%s (%s)"
                                           (:accession/code row)
                                           (:taxon/name row))
                             :code (:accession/code row)
                             :id (:accession/id row)}))

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
