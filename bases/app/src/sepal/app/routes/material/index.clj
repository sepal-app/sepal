(ns sepal.app.routes.material.index
  (:require [clojure.string :as str]
            [lambdaisland.uri :as uri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.export :as export]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.combobox :as ui.combobox]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.code-template.interface :as ct.i]
            [sepal.database.interface :as db.i]
            [sepal.material.interface.permission :as material.perm]
            [sepal.material.interface.search]
            [sepal.search.interface :as search.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn create-button []
  (pages.list/create-button :href (z/url-for material.routes/new)))

(defn- row-attrs [row]
  (let [id (:material/id row)]
    (pages.list/row-attrs :id id
                          :panel-url (z/url-for material.routes/panel {:id id}))))

(defn- stacked-summary
  "What the code cell shows below 640px, where the table collapses to a single
  column. Taxon and location are what tell two materials apart on a bench.

  Markup rather than a joined string, so the name keeps the serif it carries
  in every other place this app prints one."
  [row]
  (list (taxon-name/render (:taxon/name row))
        (when-let [loc (:location/code row)]
          (str " \u00b7 " loc))))

(defn table-columns [& {:keys [separator]}]
  ;; One identifier, not two. A material's code is issued within its accession,
  ;; so `2026.0001.01` is how a garden writes the whole thing down — and a
  ;; separate Accession column only repeated the prefix. The accession stays one
  ;; click away: clicking the row opens the preview panel, which links it.
  ;; Linking the two halves separately was the other option and was rejected —
  ;; the second half is a couple of characters wide and nowhere near a tap
  ;; target.
  [{:name "Code"
    :type :identifier-compound
    :priority 1
    :stacked stacked-summary
    :cell (fn [row] [:a {:href (z/url-for material.routes/detail
                                          {:id (:material/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (ct.i/full-code separator (:accession/code row) (:material/code row))])}
   {:name "Taxon"
    :type :name
    :priority 2
    :cell (fn [row] [:a {:href (z/url-for taxon.routes/detail
                                          {:id (:taxon/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (taxon-name/render (:taxon/name row))])}
   {:name "Location"
    :type :text
    :priority 3
    :cell (fn [row] [:a {:href (z/url-for location.routes/detail
                                          {:id (:location/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:location/code row)])}
   {:name "Status"
    :type :text
    :priority 4
    :cell (fn [row] (some-> (:material/status row) name str/capitalize))}])

(def living-term "status:alive")

(defn- living-only-checkbox
  "Checkbox that adds or removes `status:alive` in the search query. Works like
  the taxa list's accessions-only checkbox."
  [search-query]
  (let [checked? (boolean (some #{living-term} (re-seq #"\S+" (or search-query ""))))]
    [:label {:class "ml-4 flex items-center gap-2 text-sm cursor-pointer"
             :x-data (str "termFilter('q', '" living-term "', " checked? ")")}
     [:input {:type "checkbox"
              :class "spl-checkbox"
              :x-bind:checked "checked"
              :x-on:click.prevent "toggle()"}]
     [:span "Only living material"]]))

(defn index-rows
  "The <tr>s alone, for an infinite-scroll response. Same renderer as the
  initial load, so an appended row is built like one already present."
  [& {:keys [rows page page-size href separator total]}]
  (table/rows-only :columns (table-columns :separator separator)
                   :rows rows
                   :row-attrs row-attrs
                   :href href
                   :page page
                   :page-size page-size
                   :total total))

(defn table [& {:keys [rows page href page-size separator total search-query]}]
  (pages.list/card-table
    (table/table :columns (table-columns :separator separator)
                 :rows rows
                 :row-attrs row-attrs
                 :href href
                 :page page
                 :page-size page-size
                 :total total
                 :empty-state (pages.list/empty-list
                                :noun "material"
                                :body "Material is what an accession became in the garden — a plant in a bed, a
                              seed lot in store."
                                :searching? (seq search-query)
                                :create-href (z/url-for material.routes/new)))))

(defn render [& {:keys [accession field-options viewer href page page-size rows search-query separator taxon total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (table :href href
                                :page page
                                :page-size page-size
                                :rows rows
                                :separator separator
                                :total total
                                :search-query search-query)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for material.routes/export)
                           :options export/export-options)]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields field-options
                                :placeholder "Search... (e.g., type:seed status:alive)"
                                :filters (living-only-checkbox search-query)
                                :page page
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs (cond-> []
                   taxon (conj [:a {:href (z/url-for taxon.routes/index)} "Taxa"]
                               [:a {:href (z/url-for taxon.routes/detail {:id (:taxon/id taxon)})
                                    :class "italic"}
                                (:taxon/name taxon)])
                   accession (conj [:a {:href (z/url-for accession.routes/index)} "Accessions"]
                                   [:a {:href (z/url-for accession.routes/detail {:id (:accession/id accession)})
                                        :class "italic"}
                                    (:accession/code accession)])
                   :always (conj "Material"))
    :page-title-buttons (when (authz/user-has-permission? viewer material.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]
   ;; Legacy params for backwards compatibility
   [:accession-id {:optional true} :int]
   [:location-id {:optional true} :int]
   [:taxon-id {:optional true} :int]])

(defn- normalize-query
  "Merge legacy filter params into q string for backwards compatibility."
  [{:keys [q accession-id location-id taxon-id]}]
  (cond-> (or q "")
    taxon-id (str " taxon.id:" taxon-id)
    accession-id (str " accession.id:" accession-id)
    location-id (str " location.id:" location-id)))

(defn- extract-filter-value
  "Extract the value for a specific field from AST filters."
  [ast field-name]
  (->> (:filters ast)
       (filter #(= (:field %) field-name))
       first
       :value))

(defn handler [& {:keys [::z/context query-params uri viewer]}]
  (let [{:keys [db material-separator]} context
        {:keys [page page-size] :as decoded-params} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ;; Normalize legacy params into search query
        q (normalize-query decoded-params)
        ast (search.i/parse q)

        ;; Base statement with joins needed for display columns
        ;; (taxon name, accession code, location code are shown in table)
        base-stmt {:select [:*]
                   :from [[:material :m]]
                   :join [[:accession :a] [:= :a.id :m.accession_id]
                          [:taxon :t] [:= :t.id :a.taxon_id]
                          [:location :l] [:= :l.id :m.location_id]]}

        ;; Compile search query (adds WHERE clause)
        stmt (search.i/compile-query :material ast base-stmt)

        ;; Execute queries
        total (db.i/count-bounded db stmt)
        ;; m.id last and always: this list is read a page at a time by offset,
        ;; and an order that leaves any two rows tied lets SQLite return them
        ;; in either order per page — the same row on two pages and another on
        ;; none. There was no ordering here at all, so the de facto order was
        ;; rowid; naming it keeps that and makes it total.
        rows (db.i/execute-bounded! db (assoc stmt
                                              :limit page-size
                                              :offset offset
                                              :order-by (concat (search.i/relevance-order :material ast)
                                                                [[:m.id :asc]])))

        ;; Fetch entities for breadcrumbs if filtering by ID
        taxon-id (some-> (extract-filter-value ast "taxon.id") parse-long)
        accession-id (some-> (extract-filter-value ast "accession.id") parse-long)
        taxon (when taxon-id (taxon.i/get-by-id db taxon-id))
        accession (when accession-id (accession.i/get-by-id db accession-id))]

    (cond
      ;; The combobox asks for its rows as markup, so what an option looks
      ;; like is decided here with the rest of the UI.
      (some? (get query-params "options"))
      (html/render-partial
        (ui.combobox/options-fragment
          :total total
          :items (for [material rows]
                   {:id (:material/id material)
                    :text (format "%s (%s)"
                                  (ct.i/full-code material-separator
                                                  (:accession/code material)
                                                  (:material/code material))
                                  (:taxon/name material))
                    :content (ui.combobox/option-content
                               :icon (lucide/sprout)
                               :title (ct.i/full-code material-separator
                                                      (:accession/code material)
                                                      (:material/code material))
                               :meta (taxon-name/render (:taxon/name material)))})))
      ;; Infinite scroll: the sentinel asks for the next page's rows alone and
      ;; swaps itself out for them.
      (some? (get query-params "rows"))
      (html/render-partial
        (index-rows :rows rows
                    :page page
                    :page-size page-size
                    :separator material-separator
                    :total total
                    :href (uri/uri-str {:path uri
                                        :query (uri/map->query-string
                                                 (cond-> {} (seq q) (assoc :q q)))})))

      :else
      (render :viewer viewer
              :accession accession
              :field-options (search.i/field-options :material)
              :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string
                                           (cond-> {:page page}
                                             (seq q) (assoc :q q)))})
              :rows rows
              :page page
              :page-size page-size
              :search-query q
              :separator material-separator
              :taxon taxon
              :total total))))
