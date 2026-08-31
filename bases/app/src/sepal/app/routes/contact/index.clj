(ns sepal.app.routes.contact.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.contact.export :as export]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.contact.interface.permission :as contact.perm]
            [sepal.contact.interface.search]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn create-button []
  (pages.list/create-button :href (z/url-for contact.routes/new)))

(defn row-attrs [row]
  (let [id (:contact/id row)]
    (pages.list/row-attrs :id id
                          :panel-url (z/url-for contact.routes/panel {:id id}))))

(defn- stacked-summary
  "What the name cell shows below 640px, where the table collapses to a single
  column. Business and email are how a contact is recognised."
  [l]
  (table/summary (:contact/business l) (:contact/email l)))

(defn table-columns []
  [{:name "Name"
    :type :text
    :priority 1
    :stacked stacked-summary
    :cell (fn [l] [:a {:href (z/url-for contact.routes/detail
                                        {:id (:contact/id l)})
                       :class "spl-link"
                       :x-on:click.stop ""}
                   (:contact/name l)])}
   {:name "Business"
    :type :text
    :priority 2
    :cell :contact/business}
   {:name "Email"
    :type :text
    :priority 2
    :cell :contact/email}
   {:name "Phone"
    :type :text
    :priority 3
    :cell :contact/phone}])

(defn index-rows
  "The <tr>s alone, for an infinite-scroll response. Same renderer as the
  initial load, so an appended row is built like one already present."
  [& {:keys [rows page-num page-size href total]}]
  (table/rows-only :columns (table-columns)
                   :rows rows
                   :row-attrs row-attrs
                   :href href
                   :page page-num
                   :page-size page-size
                   :total total))

(defn table [& {:keys [rows page-num href page-size total search-query]}]
  (table/card-table
    (table/table :columns (table-columns)
                 :rows rows
                 :row-attrs row-attrs
                 :href href
                 :page page-num
                 :page-size page-size
                 :total total
                 :empty-state (pages.list/empty-list
                                :noun "contacts"
                                :body "The nurseries, gardens and collectors your material comes from."
                                :searching? (seq search-query)
                                :create-href (z/url-for contact.routes/new)))))

(defn render [& {:keys [field-options viewer href page-num page-size rows search-query total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (table :href href
                                :page-num page-num
                                :page-size page-size
                                :rows rows
                                :total total
                                :search-query search-query)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for contact.routes/export)
                           :options export/export-options)]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields field-options
                                :placeholder "Search... (e.g., business:nursery)"
                                :page page-num
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs ["Contacts"]
    :page-title-buttons (when (authz/user-has-permission? viewer contact.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default default-page-size} :int]
   [:q :string]])

(defn handler [& {:keys [::z/context headers query-params uri viewer]}]
  (let [{:keys [db]} context
        {:keys [page page-size q]} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ;; Parse search query
        ast (search.i/parse q)

        ;; Base statement
        base-stmt {:select [:c.*]
                   :from [[:contact :c]]}

        ;; Compile search query (adds WHERE clause)
        stmt (search.i/compile-query :contact ast base-stmt)

        ;; Execute queries
        total (db.i/count db stmt)
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      :order-by [:c.name]))]

    (cond
      (= (get headers "accept") "application/json")
      (json/json-response (for [contact rows]
                            {:name (:contact/name contact)
                             :text (format "%s (%s)"
                                           (:contact/business contact)
                                           (:contact/name contact))
                             :id (:contact/id contact)
                             :business (:contact/business contact)
                             :description (:contact/description contact)}))
      ;; Infinite scroll: the sentinel asks for the next page's rows alone and
      ;; swaps itself out for them.
      (some? (get query-params "rows"))
      (html/render-partial
        (index-rows :rows rows
                    :page-num page
                    :page-size page-size
                    :total total
                    :href (uri/uri-str {:path uri
                                        :query (uri/map->query-string
                                                 (cond-> {} (seq q) (assoc :q q)))})))

      :else
      (render :viewer viewer
              :field-options (search.i/field-options :contact)
              :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string
                                           (cond-> {:page page}
                                             (seq q) (assoc :q q)))})
              :rows rows
              :page-num page
              :page-size page-size
              :search-query q
              :total total))))
