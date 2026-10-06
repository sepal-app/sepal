(ns sepal.app.routes.propagation.index
  "The propagation list, which is also the nursery list.

  There is no separate nursery screen: `status:active` is applied by default
  when the query says nothing about status, so the page opens as the worklist
  and the same URL with `status:*` answers the whole history."
  (:require [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.list-query :as list-query]
            [sepal.app.list-view :as list-view]
            [sepal.app.params :as params]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.propagation.shared :as shared]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.location-path :as location-path]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.app.ui.tooltip :as tooltip]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr]]
            [sepal.propagation.interface.permission :as propagation.perm]
            [sepal.propagation.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn create-button []
  (pages.list/create-button :href (z/url-for propagation.routes/new)))

(defn default-status-ast
  "Apply the nursery default: `status:active` unless the query says otherwise.

  A query that names a status wins. `status:*` removes the status filter and
  the default with it, which is how the page answers for the whole history.

  Public because the CSV export has to see the same rows the page showed."
  [ast]
  (let [status-filter? #(= "status" (:field %))
        status-filters (filter status-filter? (:filters ast))]
    (cond
      (some #(= "*" (:value %)) status-filters)
      (update ast :filters #(vec (remove status-filter? %)))

      (seq status-filters)
      ast

      :else
      (update ast :filters conj {:field "status"
                                 :value "active"
                                 :negated false}))))

(defn- parent-label
  "`2026.0042` when the parent is the accession alone, `2026.0042.1` when an
  individual plant narrows it."
  [row separator]
  (shared/parent-name row
                      (when (:material/code row) row)
                      separator))

(defn- row-attrs [row]
  (let [id (:propagation/id row)]
    (pages.list/row-attrs :id id
                          :panel-url (z/url-for propagation.routes/panel {:id id}))))

(defn- has-product?
  "The EXISTS probe rides along in the row as `:propagation/has-product`."
  [row]
  (let [v (:propagation/has-product row)]
    (cond
      (boolean? v) v
      (nil? v) false
      :else (not (zero? v)))))

(defn- status-badge [status label]
  (let [colors {"active" "spl-badge--info"
                "complete" "spl-badge--ok"
                "failed" "spl-badge--danger"}]
    [:span {:class (html/attr "spl-badge" (get colors status "spl-badge--neutral"))}
     label]))

(defn- rootstock-query [stmt]
  (-> stmt
      (list-query/add-left-join [:taxon :rt] [:= :rt.id :p.rootstock_taxon_id])
      (list-query/add-select [:rt.name :propagation__rootstock_name])))

(defn table-columns [& {:keys [type-labels status-labels separator timezone]}]
  (into
    [{:name (tr "Parent")
      :key :parent
      :sort [:a.code :m.code]
      :type :identifier
      :priority 1
      :stacked (fn [row]
                 (table/summary (parent-label row separator)
                                (get type-labels (:propagation/type row))
                                (get status-labels (:propagation/status row))))
      :cell (fn [row]
              [:a {:href (z/url-for propagation.routes/detail {:id (:propagation/id row)})
                   :class "spl-link"
                   :x-on:click.stop ""}
               (parent-label row separator)])}
     {:name (tr "Type")
      :key :type
      :sort [:p.type]
      :type :text
      :priority 2
      :cell (fn [row] (get type-labels (:propagation/type row) (:propagation/type row)))}
     {:name (tr "Status")
      :key :status
      :sort [:p.status]
      :type :text
      :priority 3
      :cell (fn [row]
              (status-badge (:propagation/status row)
                            (get status-labels (:propagation/status row)
                                 (:propagation/status row))))}
     {:name (tr "Propagated")
      :key :propagated
      :sort [:p.propagated_on]
      :type :date
      :priority 4
      :cell :propagation/propagated-on}
     {:name (tr "Started")
      :key :started
      :sort [:p.quantity_started]
      :type :number
      :priority 5
      :cell :propagation/quantity-started}
     {:name (tr "Succeeded")
      :key :succeeded
      :sort [:p.quantity_succeeded]
      :type :number
      :priority 6
      :cell (fn [row]
              (let [succeeded (:propagation/quantity-succeeded row)]
                (list
                  (when (and (some? succeeded) (not (has-product? row)))
                    ;; A success count with nothing recorded as its result is a
                    ;; to-do, not an error: something struck and nobody wrote
                    ;; down where it went. This is the only place the count is
                    ;; compared against anything.
                    (tooltip/wrap
                      (lucide/triangle-alert :class "w-4 h-4 text-danger")
                      (tr "No product recorded for this batch")
                      :side "left"))
                  (when (some? succeeded) (str succeeded)))))}
     {:name (tr "Location")
      :key :location
      :sort [[:lower :l.name]]
      :type :text
      :priority 7
      :cell (fn [row]
              (when-let [location-id (:propagation/location-id row)]
                [:a {:href (z/url-for location.routes/detail {:id location-id})
                     :class "spl-link"
                     :x-on:click.stop ""}
                 (:location/path row)]))}
     {:name (tr "Succeeded on")
      :key :succeeded-on
      :type :date
      :priority 3
      :hidden? true
      :sort [:p.succeeded_on]
      :cell :propagation/succeeded-on}
     {:name (tr "Rootstock")
      :key :rootstock
      :type :name
      :priority 3
      :hidden? true
      :query rootstock-query
      :sort [:rt.name]
      :cell #(some-> (:propagation/rootstock-name %) taxon-name/render)}]
    (table/timestamp-columns :created [:p.created_at :propagation/created-at]
                             :updated [:p.updated_at :propagation/updated-at]
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
                                        :title (tr "No propagations yet")
                                        :body (tr "What the garden has grown itself, and what came out of it.")
                                        :searching? (seq search-query)
                                        :create-href (z/url-for propagation.routes/new))}))))

(defn render [& {:keys [field-options href page-num page-size rows search-query
                        table-opts total viewer]}]
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
                           :export-action (z/url-for propagation.routes/export)
                           :options [])]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields field-options
                                :placeholder (tr "Search... (e.g., status:active type:cutting)")
                                :page page-num
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs [(tr "Propagation")]
    :page-title-buttons (when (authz/user-has-permission? viewer propagation.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default default-page-size} :int]
   [:q :string]])

(defn handler [& {:keys [::z/context query-params uri viewer] :as request}]
  (let [{:keys [db material-separator timezone]} context
        {:keys [page page-size q]} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ast (default-status-ast (search.i/parse q))

        ;; Joins for the table's own columns: the parent accession, the named
        ;; plant when there is one, and the bench. Search fields add their own
        ;; joins on top; the compiler deduplicates by alias.
        base-stmt {:select [:p.*
                            :a.code
                            :m.code
                            :l.name
                            [[[:or
                               [:exists {:select [1]
                                         :from [[:material :pm]]
                                         :where [:= :pm.propagation_id :p.id]}]
                               [:exists {:select [1]
                                         :from [[:accession :pa]]
                                         :where [:= :pa.propagation_id :p.id]}]]]
                             :propagation__has_product]]
                   :from [[:propagation :p]]
                   :join [[:accession :a] [:= :a.id :p.parent_accession_id]]
                   :left-join [[:material :m] [:= :m.id :p.parent_material_id]
                               [:location :l] [:= :l.id :p.location_id]]}

        stmt (search.i/compile-query :propagation ast base-stmt {:timezone timezone})

        total (db.i/count-bounded db stmt)
        type-labels (shared/type-labels db)
        status-labels (shared/status-labels db)
        view (list-view/resolve request :propagation
                                (table-columns :type-labels type-labels
                                               :status-labels status-labels
                                               :separator material-separator
                                               :timezone timezone))
        rows (db.i/execute-bounded! db (-> stmt
                                           (list-query/with-columns (:columns view) (:sort view))
                                           (assoc :limit page-size
                                                  :offset offset
                                                  :order-by (list-query/order-by
                                                              (:sort view)
                                                              {:relevance (search.i/relevance-order :propagation ast)
                                                               :default [[:p.propagated_on :desc] [:p.id :desc]]
                                                               :tiebreak [:p.id :asc]}))))
        rows (let [paths (location-path/by-id db (keep :propagation/location-id rows))]
               (mapv #(assoc % :location/path (get paths (:propagation/location-id %))) rows))
        table-opts (list-view/table-opts view uri q)]

    (if (some? (get query-params "rows"))
      ;; Infinite scroll: the sentinel asks for the next page's rows alone and
      ;; swaps itself out for them.
      (html/render-partial
        (index-rows :rows rows
                    :page-num page
                    :page-size page-size
                    :total total
                    :table-opts table-opts
                    :href (list-view/href view uri q)))
      (list-view/respond
        view
        (render :field-options (search.i/field-options :propagation)
                :href (list-view/href view uri q :page page)
                :rows rows
                :page-num page
                :page-size page-size
                :search-query q
                :table-opts table-opts
                :total total
                :viewer viewer)
        uri q))))
