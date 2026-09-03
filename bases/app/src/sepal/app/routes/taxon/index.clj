(ns sepal.app.routes.taxon.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.taxon.export :as export]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [sepal.synonym.interface :as synonym.i]
            [sepal.taxon.interface.permission :as taxon.perm]
            [sepal.taxon.interface.search]
            [zodiac.core :as z]))

(defn create-button []
  (pages.list/create-button :href (z/url-for taxon.routes/new)))

(defn- stacked-summary
  "What the name cell shows below 640px, where the table collapses to a single
  column. Rank and author are what tell two similar names apart."
  [t]
  (table/summary (:taxon/rank t) (:taxon/author t)))

(defn table-columns []
  [{:name "Name"
    :type :name
    :priority 1
    :stacked stacked-summary
    :cell (fn [t]
            [:a {:href (z/url-for taxon.routes/detail
                                  {:id (:taxon/id t)})
                 :class "spl-link"
                 :x-on:click.stop ""} ; Stop propagation so row click doesn't fire
             (taxon-name/render (:taxon/name t))])}
   {:name "Author"
    :type :text
    :priority 2
    :cell :taxon/author}
   {:name "Rank"
    :type :text
    :priority 3
    :cell :taxon/rank}
   {:name "Parent"
    :type :name
    :priority 3
    :cell (fn [t]
            (when (:taxon/parent-id t)
              [:a {:href (z/url-for taxon.routes/detail
                                    {:id (:taxon/parent-id t)})
                   :class "spl-link"
                   :x-on:click.stop ""} ; Stop propagation
               (:taxon/parent-name t)]))}])

(defn- row-attrs [row]
  (let [id (:taxon/id row)]
    (pages.list/row-attrs :id id
                          :panel-url (z/url-for taxon.routes/panel {:id id}))))

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

(def ^:private synonym-block-limit 5)

(defn synonym-matches-block
  "Names that matched a synonym rather than a taxon's own name.

  Deliberately not merged into the table: a synonym match has no author, rank
  or parent, and `total` counts only the taxon query, so a merged row would
  render blank cells and break pagination."
  [& {:keys [matches]}]
  (when (seq matches)
    (let [shown (take synonym-block-limit matches)
          extra (- (count matches) (count shown))]
      [:div {:class "spl-alert spl-alert--info"}
       [:p "Also matching a synonym"]
       [:ul
        (for [m shown]
          [:li
           [:a {:href (z/url-for taxon.routes/detail {:id (:taxon/id m)})
                :class "spl-link"}
            (taxon-name/render (:taxon/name m))]
           [:span " — matches synonym "]
           (taxon-name/render (:synonym/synonym-name m))])]
       (when (pos? extra)
         [:p (format "and %d more" extra)])])))

(defn table [& {:keys [rows page href page-size total search-query]}]
  (table/card-table
    (table/table :columns (table-columns)
                 :rows rows
                 :row-attrs row-attrs
                 :href href
                 :page page
                 :page-size page-size
                 :total total
                 :empty-state (pages.list/empty-list
                                :noun "taxa"
                                :body "The taxonomy behind your collection. Import the World Flora Online list
                              from Settings, or add a name by hand."
                                :searching? (seq search-query)
                                :create-href (z/url-for taxon.routes/new)))))
(defn- accessions-only-checkbox
  "Checkbox that toggles `accessions:>0` filter in the search query.
   Uses Alpine.js component from js/query-builder.ts"
  [q]
  (let [has-filter? (boolean (and q (re-find #"accessions:>0" q)))]
    [:label {:class "ml-4 flex items-center gap-2 text-sm cursor-pointer"
             :x-data (str "accessionsOnlyFilter('q', " has-filter? ")")}
     [:input {:type "checkbox"
              :class "spl-checkbox"
              :x-bind:checked "checked"
              :x-on:click.prevent "toggle()"}]
     [:span "Only taxa with accessions"]]))

(defn render [& {:keys [field-options viewer href page page-size rows search-query total synonym-matches]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (synonym-matches-block :matches synonym-matches)
                         (table :href href
                                :page page
                                :page-size page-size
                                :rows rows
                                :total total
                                :search-query search-query)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for taxon.routes/export)
                           :options export/export-options)]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields field-options
                                :placeholder "Search... (e.g., rank:species Quercus)"
                                :filters (accessions-only-checkbox search-query)
                                :page page
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))

    :breadcrumbs ["Taxa"]
    :page-title-buttons (when (authz/user-has-permission? viewer taxon.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]])

(defn handler
  [& {:keys [::z/context headers query-params uri viewer]}]
  (let [{:keys [db]} context
        {:keys [page page-size q]} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ;; Parse search query
        ast (search.i/parse q)

        ;; Columns to select (including parent name for display)
        columns [[:t.id :id]
                 [:t.name :name]
                 [:t.rank :rank]
                 [:t.author :author]
                 [:t.parent_id :parent-id]
                 [:p.name :parent_name]
                 [:t.wfo_taxon_id :wfo_taxon_id]]

        ;; Base statement with parent join for display
        base-stmt {:select columns
                   :from [[:taxon :t]]
                   :left-join [[:taxon :p] [:= :p.id :t.parent_id]]}

        ;; Compile search query (adds WHERE clause and any filter joins)
        stmt (search.i/compile-query :taxon ast base-stmt)

        ;; Execute queries in parallel
        [rows total] (pcalls
                       #(db.i/execute! db (assoc stmt
                                                 :limit page-size
                                                 :offset offset
                                                 :order-by [[:t.name :asc]]))
                       #(db.i/count db stmt))

        ;; Synonym matches: a historical name that resolves to a taxon this
        ;; query's own name search would not find. Resolved once and used
        ;; differently by each branch below.
        synonym-matches (synonym.i/resolve context db q)]

    (cond
      ;; We return JSON for autocomplete fields. Only this branch merges the
      ;; two result sets — see index_test.clj and the task brief for why the
      ;; other branches keep them separate.
      (= (get headers "accept") "application/json")
      (let [seen (set (map :taxon/id rows))
            extra (:out (reduce (fn [{:keys [seen out]} hit]
                                  (let [id (:taxon/id hit)]
                                    (if (contains? seen id)
                                      {:seen seen :out out}
                                      {:seen (conj seen id)
                                       :out (conj out hit)})))
                                {:seen seen :out []}
                                synonym-matches))]
        (json/json-response
          (concat
            (for [taxon rows]
              {:text (:taxon/name taxon)
               :name (:taxon/name taxon)
               :id (:taxon/id taxon)
               :rank (:taxon/rank taxon)
               :author (:taxon/author taxon)
               :parentId (:taxon/parent-id taxon)
               :parentName (:taxon/parent-name taxon)})
            (for [hit extra]
              {:text (:taxon/name hit)
               :name (:taxon/name hit)
               :id (:taxon/id hit)
               :matchedSynonym (:synonym/synonym-name hit)}))))

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
      (let [row-ids (set (map :taxon/id rows))
            ;; Dedupe on taxon id, keeping the first synonym for each, and drop
            ;; a taxon already present in `rows` — it matched by its own name
            ;; and needs no repeating. `rows` is only the current page, so a
            ;; taxon on a later page can still appear in both the block and
            ;; the table; that is acceptable rather than worth a second query
            ;; to prevent.
            block-matches (:out (reduce (fn [{:keys [seen out]} hit]
                                          (let [id (:taxon/id hit)]
                                            (if (or (contains? row-ids id) (contains? seen id))
                                              {:seen seen :out out}
                                              {:seen (conj seen id) :out (conj out hit)})))
                                        {:seen #{} :out []}
                                        synonym-matches))]
        (render :viewer viewer
                :field-options (search.i/field-options :taxon)
                :href (uri/uri-str {:path uri
                                    :query (uri/map->query-string
                                             (cond-> {:page page}
                                               (seq q) (assoc :q q)))})
                :rows rows
                :page page
                :page-size page-size
                :search-query q
                :total total
                :synonym-matches block-matches)))))
