(ns sepal.app.routes.propagation.index
  "The propagation list, which is also the nursery list.

  There is no separate nursery screen: `status:active` is applied by default
  when the query says nothing about status, so the page opens as the worklist
  and the same URL with `status:*` answers the whole history."
  (:require [lambdaisland.uri :as uri]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.propagation.shared :as shared]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.app.ui.tooltip :as tooltip]
            [sepal.database.interface :as db.i]
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

(defn table-columns [& {:keys [type-labels status-labels separator]}]
  [{:name "Parent"
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
   {:name "Type"
    :type :text
    :priority 2
    :cell (fn [row] (get type-labels (:propagation/type row) (:propagation/type row)))}
   {:name "Status"
    :type :text
    :priority 3
    :cell (fn [row]
            (status-badge (:propagation/status row)
                          (get status-labels (:propagation/status row)
                               (:propagation/status row))))}
   {:name "Propagated"
    :type :date
    :priority 4
    :cell :propagation/propagated-on}
   {:name "Started"
    :type :number
    :priority 5
    :cell :propagation/quantity-started}
   {:name "Succeeded"
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
                    "No product recorded for this batch"
                    :side "left"))
                (when (some? succeeded) (str succeeded)))))}
   {:name "Location"
    :type :text
    :priority 7
    :cell (fn [row]
            (when-let [location-id (:propagation/location-id row)]
              [:a {:href (z/url-for location.routes/detail {:id location-id})
                   :class "spl-link"
                   :x-on:click.stop ""}
               (:location/name row)]))}])

(defn index-rows
  "The <tr>s alone, for an infinite-scroll response. Same renderer as the
  initial load, so an appended row is built like one already present."
  [& {:keys [rows page-num page-size href total type-labels status-labels separator]}]
  (table/rows-only :columns (table-columns :type-labels type-labels
                                           :status-labels status-labels
                                           :separator separator)
                   :rows rows
                   :row-attrs row-attrs
                   :href href
                   :page page-num
                   :page-size page-size
                   :total total))

(defn table [& {:keys [rows page-num href page-size total search-query
                       type-labels status-labels separator]}]
  (pages.list/card-table
    (table/table :columns (table-columns :type-labels type-labels
                                         :status-labels status-labels
                                         :separator separator)
                 :rows rows
                 :row-attrs row-attrs
                 :href href
                 :page page-num
                 :page-size page-size
                 :total total
                 :empty-state (pages.list/empty-list
                                :noun "propagations"
                                :body "What the garden has grown itself, and what came out of it."
                                :searching? (seq search-query)
                                :create-href (z/url-for propagation.routes/new)))))

(defn render [& {:keys [field-options href page-num page-size rows search-query
                        total type-labels status-labels separator viewer]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (table :href href
                                :page-num page-num
                                :page-size page-size
                                :rows rows
                                :total total
                                :search-query search-query
                                :type-labels type-labels
                                :status-labels status-labels
                                :separator separator)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for propagation.routes/export)
                           :options [])]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields field-options
                                :placeholder "Search... (e.g., status:active type:cutting)"
                                :page page-num
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs ["Propagation"]
    :page-title-buttons (when (authz/user-has-permission? viewer propagation.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default default-page-size} :int]
   [:q :string]])

(defn handler [& {:keys [::z/context query-params uri viewer]}]
  (let [{:keys [db material-separator]} context
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

        stmt (search.i/compile-query :propagation ast base-stmt)

        total (db.i/count-bounded db stmt)
        rows (db.i/execute-bounded! db (assoc stmt
                                              :limit page-size
                                              :offset offset
                                              :order-by (concat (search.i/relevance-order :propagation ast)
                                                                [[:p.propagated_on :desc]
                                                                 [:p.id :desc]])))
        type-labels (shared/type-labels db)
        status-labels (shared/status-labels db)]

    (if (some? (get query-params "rows"))
      ;; Infinite scroll: the sentinel asks for the next page's rows alone and
      ;; swaps itself out for them.
      (html/render-partial
        (index-rows :rows rows
                    :page-num page
                    :page-size page-size
                    :total total
                    :type-labels type-labels
                    :status-labels status-labels
                    :separator material-separator
                    :href (uri/uri-str {:path uri
                                        :query (uri/map->query-string
                                                 (cond-> {} (seq q) (assoc :q q)))})))
      (render :field-options (search.i/field-options :propagation)
              :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string
                                           (cond-> {:page page}
                                             (seq q) (assoc :q q)))})
              :rows rows
              :page-num page
              :page-size page-size
              :search-query q
              :total total
              :type-labels type-labels
              :status-labels status-labels
              :separator material-separator
              :viewer viewer))))
