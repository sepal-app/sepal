(ns sepal.app.routes.observation.index
  (:require [clojure.string :as str]
            [lambdaisland.uri :as uri]
            [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.observation.export :as export]
            [sepal.app.routes.observation.routes :as observation.routes]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [sepal.observation.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z])
  (:import [java.time LocalDate]))

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
  [row]
  (case (:observation/resource-type row)
    "material" (str (:accession/code row) "." (:material/code row))
    "location" (cond-> (:location/name row)
                 (:location/code row) (str (format " (%s)" (:location/code row))))
    nil))

(defn- subject-href [row]
  (case (:observation/resource-type row)
    "material" (z/url-for material.routes/detail {:id (:observation/resource-id row)})
    "location" (z/url-for location.routes/detail {:id (:observation/resource-id row)})
    nil))

(defn table-columns []
  [{:name "Type"
    :type :text
    :priority 1
    :stacked (fn [row] (table/summary (subject-label row) (:observation/observed-on row)))
    :cell (fn [row]
            [:a {:href (z/url-for observation.routes/detail {:id (:observation/id row)})
                 :class "spl-link"}
             (:observation/type-label row)
             (when-let [value-label (:observation/value-label row)]
               (str ": " value-label))])}
   {:name "Subject"
    :type :text
    :priority 2
    :cell (fn [row]
            [:a {:href (subject-href row)
                 :class "spl-link"}
             (subject-label row)])}
   {:name "Observed"
    :type :date
    :priority 3
    :cell :observation/observed-on}
   {:name "Due"
    :type :date
    :priority 4
    :cell :observation/next-check-on}
   {:name "Observer"
    :type :text
    :priority 5
    :cell :observation/observed-by}])

(defn index-rows
  "The <tr>s alone, for an infinite-scroll response. Same renderer as the
  initial load, so an appended row is built like one already present."
  [& {:keys [rows page page-size href total]}]
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
                                :noun "observations"
                                :body "What a curator saw, dated and filed against the material or location it
                              was about."
                                :searching? (seq search-query)))))

(defn- query-tokens [q]
  (->> (str/split (or q "") #"\s+")
       (remove str/blank?)))

(defn overdue-term
  "The overdue filter term the index's own toggle applies: `due:<=<today>`.
  Public so another page linking in to the overdue filter builds the
  identical term rather than risking drift."
  [today]
  (str "due:<=" today))

(defn- toggle-overdue-query
  "The query the Overdue link points at: the overdue term added to whatever
  is already there, or removed from it if it is already applied. Additive
  rather than replacing, so ticking it narrows an existing search instead of
  discarding it."
  [search-query today]
  (let [term (overdue-term today)
        tokens (query-tokens search-query)]
    (if (some #{term} tokens)
      (str/join " " (remove #{term} tokens))
      (str/join " " (conj (vec tokens) term)))))

(defn- overdue-toggle
  "A one-click way to reach the overdue filter, rather than something a
  curator has to know the `due:<=<today>` syntax to type."
  [search-query today]
  (let [term (overdue-term today)
        active? (contains? (set (query-tokens search-query)) term)
        target (toggle-overdue-query search-query today)]
    [:a {:class (html/attr "spl-btn" "spl-btn--sm"
                           (if active? "spl-btn--primary" "spl-btn--ghost")
                           "gap-1")
         :href (str "?" (uri/map->query-string (cond-> {} (seq target) (assoc :q target))))}
     (lucide/triangle-alert :class "size-4")
     [:span (if active? "Showing overdue" "Overdue")]]))

(defn render [& {:keys [href page page-size rows search-query total today]}]
  (ui.page/page
    :content (pages.list/page-content
               :content [:div
                         (table :href href
                                :page page
                                :page-size page-size
                                :rows rows
                                :total total
                                :search-query search-query)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for observation.routes/export)
                           :options export/export-options)]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields (search.i/field-options :observation)
                                :placeholder "Search... (e.g., type:phenology value:flowering)"
                                :filters (overdue-toggle search-query today)
                                :page page
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs ["Observations"]))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default default-page-size} :int]
   [:q :string]])

(defn- base-stmt []
  {:select [:o.*
            [:t.label :observation__type_label]
            [:v.label :observation__value_label]
            :acc.code
            :m.code
            :l.code
            :l.name]
   :from [[:observation :o]]
   :join-by [:left [[:observation_type :t] [:= :t.code :o.type]]
             :left [[:observation_value :v]
                    [:and [:= :v.type :o.type] [:= :v.code :o.value]]]
             :left [[:material :m]
                    [:and [:= :o.resource_type "material"] [:= :m.id :o.resource_id]]]
             :left [[:accession :acc] [:= :acc.id :m.accession_id]]
             :left [[:location :l]
                    [:and [:= :o.resource_type "location"] [:= :l.id :o.resource_id]]]]})

(defn handler [& {:keys [::z/context query-params uri]}]
  (let [{:keys [db]} context
        {:keys [page page-size q]} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ast (search.i/parse q)
        stmt (search.i/compile-query :observation ast (base-stmt))

        total (db.i/count-bounded db stmt)
        ;; Newest observed first, the same order the subject's own Observations
        ;; tab reads in; id breaks a tie observed_on cannot, the same reasoning
        ;; material and location's own indexes give for their tiebreakers.
        rows (db.i/execute-bounded! db (assoc stmt
                                              :limit page-size
                                              :offset offset
                                              :order-by (concat (search.i/relevance-order :observation ast)
                                                                [[:o.observed_on :desc] [:o.id :desc]])))
        today (str (LocalDate/now))]

    (cond
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
      (render :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string
                                           (cond-> {:page page}
                                             (seq q) (assoc :q q)))})
              :rows rows
              :page page
              :page-size page-size
              :search-query q
              :total total
              :today today))))
