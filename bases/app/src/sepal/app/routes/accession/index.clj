(ns sepal.app.routes.accession.index
  (:require [lambdaisland.uri :as uri]
            [sepal.accession.interface.permission :as accession.perm]
            [sepal.accession.interface.search]
            [sepal.app.authorization :as authz]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.export :as export]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.query-builder :as query-builder]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn create-button []
  [:a {:class "btn btn-primary"
       :href (z/url-for accession.routes/new)}
   "Create"])

(defn- row-attrs
  "Generate attributes for a table row to enable panel preview."
  [row]
  (let [id (:accession/id row)
        panel-url (z/url-for accession.routes/panel {:id id})]
    {:class "hover:bg-base-200/50 cursor-pointer"
     :x-bind:class (str "selectedId === " id " ? 'bg-base-200' : ''")
     :x-on:click (str "selectRow(" id ", $el)")
     :hx-get panel-url
     :hx-trigger "panel-select"
     :hx-target (str "#" pages.list/panel-container-id)
     :hx-swap "innerHTML"
     :hx-push-url "false"}))

(defn table-columns []
  [{:name "Code"
    :cell (fn [row] [:a {:href (z/url-for accession.routes/detail
                                          {:id (:accession/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:accession/code row)])}
   {:name "Taxon"
    :cell (fn [row] [:a {:href (z/url-for taxon.routes/detail
                                          {:id (:taxon/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:taxon/name row)])}])

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
                                 :fields field-options)
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

    (if (= (get headers "accept") "application/json")
      (json/json-response (for [row rows]
                            {:text (format "%s (%s)"
                                           (:accession/code row)
                                           (:taxon/name row))
                             :code (:accession/code row)
                             :id (:accession/id row)}))
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
