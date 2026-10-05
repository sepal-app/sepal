(ns sepal.app.routes.observation.index
  (:require [sepal.app.authorization :as authz]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.list-query :as list-query]
            [sepal.app.list-view :as list-view]
            [sepal.app.params :as params]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.observation.export :as export]
            [sepal.app.routes.observation.routes :as observation.routes]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.location-path :as location-path]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.code-template.interface :as ct.i]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr trc]]
            [sepal.observation.interface :as observation.i]
            [sepal.observation.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn- row-attrs
  "No preview panel exists for an observation -- there is no `/panel/` route
  to open -- so this carries only an id for tests and tooling to find a row
  by, the way `data-observation-id` already marks one in the subject's own
  Observations tab."
  [row]
  {:data-observation-id (:observation/id row)})

(defn- subject-label
  "What the row's Subject cell and the Type cell's stacked summary show. There
  is no join for this in the search component -- resource_id carries no
  foreign key -- so the index's own base statement joins both the material
  and location tables, one of which is always null per row."
  [row separator]
  (case (:observation/resource-type row)
    "material" (ct.i/full-code separator (:accession/code row) (:material/code row))
    "location" (cond-> (or (:location/path row) (:location/name row))
                 (:location/code row) (str (format " (%s)" (:location/code row))))
    nil))

(defn- subject-href
  "The subject's Observations tab, where an editor can act on what is due. A
  reader can't open that tab, so their link goes to the subject's own page."
  [row viewer]
  (let [params {:id (:observation/resource-id row)}
        editor? (authz/can-edit? viewer)]
    (case (:observation/resource-type row)
      "material" (z/url-for (if editor? material.routes/detail-observations material.routes/detail) params)
      "location" (z/url-for (if editor? location.routes/detail-observations location.routes/detail) params)
      nil)))

(defn- type-summary [row]
  (str (some->> (:observation/type-label row) (trc "observation_type"))
       (when-let [value-label (:observation/value-label row)]
         (str ": " (trc "observation_value" value-label)))))

(defn table-columns [viewer separator timezone]
  (into
    [{:name (tr "Subject")
      :key :subject
      :type :text
      :priority 1
      :stacked (fn [row] (table/summary (subject-label row separator) (type-summary row)))
      :cell (fn [row]
              [:a {:href (subject-href row viewer)
                   :class "spl-link"}
               (subject-label row separator)])}
     {:name (tr "Type") :key :type :type :text :priority 2
      :sort [:o.type] :cell type-summary}
     {:name (tr "Observed") :key :observed :type :date :priority 3
      :sort [:o.observed_on] :cell :observation/observed-on}
     {:name (tr "Due") :key :due :type :date :priority 4
      :sort [:o.next_check_on] :cell :observation/next-check-on}
     {:name (tr "Observer") :key :observer :type :text :priority 5
      :cell observation.i/observer}
     {:name (tr "Value") :key :value :type :text :priority 3 :hidden? true
      :sort [:o.value]
      :cell #(some->> (:observation/value-label %) (trc "observation_value"))}
     {:name (tr "Note") :key :note :type :text :priority 3 :hidden? true
      :cell :observation/note}]
    (table/timestamp-columns :created [:o.created_at :observation/created-at]
                             :updated [:o.updated_at :observation/updated-at]
                             :timezone timezone)))

(defn index-rows
  "The <tr>s alone, for an infinite-scroll response. Same renderer as the
  initial load, so an appended row is built like one already present."
  [& {:keys [rows page page-size href total table-opts]}]
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
                                        :title (tr "No observations yet")
                                        :body (tr "What a curator saw, dated and filed against the material or location it was about.")
                                        :searching? (seq search-query))}))))

(defn overdue-term
  "The overdue filter term the index's own checkbox applies: `overdue:<today>`,
  due by today and not followed up by a later observation. Public so another
  page linking in to the overdue filter builds the identical term rather than
  risking drift."
  [today]
  (str "overdue:" today))

(defn- overdue-only-checkbox
  "Checkbox that adds or removes the overdue term in the search query, so a
  curator doesn't have to know the `overdue:<today>` syntax. Works like the
  taxa list's accessions-only checkbox."
  [search-query today]
  (let [term (overdue-term today)
        checked? (boolean (some #{term} (re-seq #"\S+" (or search-query ""))))]
    [:label {:class "ml-4 flex items-center gap-2 text-sm cursor-pointer"
             :x-data (str "termFilter('q', '" term "', " checked? ")")}
     [:input {:type "checkbox"
              :class "spl-checkbox"
              :x-bind:checked "checked"
              :x-on:click.prevent "toggle()"}]
     [:span (tr "Only overdue observations")]]))

(defn render [& {:keys [href page page-size rows search-query table-opts total today]}]
  (ui.page/page
    :content (pages.list/page-content
               :content [:div
                         (table :href href
                                :page page
                                :page-size page-size
                                :rows rows
                                :total total
                                :search-query search-query
                                :table-opts table-opts)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for observation.routes/export)
                           :options export/export-options)]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields (search.i/field-options :observation)
                                :placeholder (tr "Search... (e.g., type:phenology value:flowering)")
                                :filters (overdue-only-checkbox search-query today)
                                :page page
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs [(tr "Observations")]))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default default-page-size} :int]
   [:q :string]])

(defn- base-stmt []
  {:select [:o.*
            [:t.label :observation__type_label]
            [:v.label :observation__value_label]
            ;; Aliased to :observation/author-email, not the bare :user/email
            ;; a plain `:u.email` would give, so the Observer column can share
            ;; observation.i/observer with the subject's own tab.
            [:u.email :observation__author_email]
            :acc.code
            :m.code
            :l.code
            :l.name]
   :from [[:observation :o]]
   :join-by [:left [[:observation_type :t] [:= :t.code :o.type]]
             :left [[:observation_value :v]
                    [:and [:= :v.type :o.type] [:= :v.code :o.value]]]
             :left [[:user :u] [:= :u.id :o.created_by]]
             :left [[:material :m]
                    [:and [:= :o.resource_type "material"] [:= :m.id :o.resource_id]]]
             :left [[:accession :acc] [:= :acc.id :m.accession_id]]
             :left [[:location :l]
                    [:and [:= :o.resource_type "location"] [:= :l.id :o.resource_id]]]]})

(defn handler [& {:keys [::z/context query-params uri viewer] :as request}]
  (let [{:keys [db material-separator timezone]} context
        {:keys [page page-size q]} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ast (search.i/parse q)
        stmt (search.i/compile-query :observation ast (base-stmt)
                                     {:material-separator material-separator})

        total (db.i/count-bounded db stmt)
        view (list-view/resolve request :observation (table-columns viewer material-separator timezone))
        ;; Newest observed first, the same order the subject's own Observations
        ;; tab reads in; id breaks a tie observed_on cannot, the same reasoning
        ;; material and location's own indexes give for their tiebreakers.
        rows (db.i/execute-bounded! db (-> stmt
                                           (list-query/with-columns (:columns view) (:sort view))
                                           (assoc :limit page-size
                                                  :offset offset
                                                  :order-by (list-query/order-by
                                                              (:sort view)
                                                              {:relevance (search.i/relevance-order :observation ast)
                                                               :default [[:o.observed_on :desc] [:o.id :desc]]
                                                               :tiebreak [:o.id :asc]}))))
        ;; A location subject reads as its path, from one query for the page.
        rows (let [location-id #(when (= "location" (:observation/resource-type %))
                                  (:observation/resource-id %))
                   paths (location-path/by-id db (keep location-id rows))]
               (mapv #(assoc % :location/path (get paths (location-id %))) rows))
        today (str (datetime/today timezone))
        table-opts (list-view/table-opts view uri q)]

    (cond
      ;; Infinite scroll: the sentinel asks for the next page's rows alone and
      ;; swaps itself out for them.
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
        (render :href (list-view/href view uri q :page page)
                :rows rows
                :page page
                :page-size page-size
                :search-query q
                :table-opts table-opts
                :total total
                :today today)
        uri q))))
