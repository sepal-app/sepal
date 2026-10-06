(ns sepal.app.routes.contact.index
  (:require [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.list-query :as list-query]
            [sepal.app.list-view :as list-view]
            [sepal.app.params :as params]
            [sepal.app.routes.contact.export :as export]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.combobox :as ui.combobox]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.contact.interface.name :as contact.name]
            [sepal.contact.interface.permission :as contact.perm]
            [sepal.contact.interface.search]
            [sepal.contact.interface.spec :as contact.spec]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr]]
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

(defn table-columns [timezone]
  (into
    [{:name (tr "Name")
      :key :name
      :type :text
      :priority 1
      :sort [[:lower :c.name]]
      :stacked stacked-summary
      :cell (fn [l] [:a {:href (z/url-for contact.routes/detail
                                          {:id (:contact/id l)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:contact/name l)])}
     {:name (tr "Business") :key :business :type :text :priority 2
      :sort [[:lower :c.business]] :cell :contact/business}
     {:name (tr "Email") :key :email :type :text :priority 2
      :sort [[:lower :c.email]] :cell :contact/email}
     {:name (tr "City") :key :city :type :text :priority 3
      :sort [[:lower :c.city]] :cell :contact/city}
     {:name (tr "Phone") :key :phone :type :text :priority 3
      :sort [:c.phone] :cell :contact/phone}
     {:name (tr "Type") :key :type :type :text :priority 3 :hidden? true
      :sort [:c.type]
      :cell #(some-> (:contact/type %) keyword contact.spec/type-labels tr)}
     {:name (tr "Country") :key :country :type :text :priority 3 :hidden? true
      :sort [[:lower :c.country]] :cell :contact/country}
     {:name (tr "Province") :key :province :type :text :priority 3 :hidden? true
      :sort [[:lower :c.province]] :cell :contact/province}
     {:name (tr "Postal code") :key :postal-code :type :text :priority 3 :hidden? true
      :sort [:c.postal_code] :cell :contact/postal-code}]
    (table/timestamp-columns :created [:c.created_at :contact/created-at]
                             :updated [:c.updated_at :contact/updated-at]
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
                                        :title (tr "No contacts yet")
                                        :body (tr "The nurseries, gardens and collectors your material comes from.")
                                        :searching? (seq search-query)
                                        :create-href (z/url-for contact.routes/new))}))))

(defn render [& {:keys [field-options viewer href page-num page-size rows search-query table-opts total]}]
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
                           :export-action (z/url-for contact.routes/export)
                           :options export/export-options)]
               :table-actions (pages.list/toolbar
                                :q search-query
                                :fields field-options
                                ;; i18n: Keep "business:" in English; it is search syntax
                                :placeholder (tr "Search... (e.g., business:nursery)")
                                :page page-num
                                :page-size page-size
                                :total total
                                :actions (ui.export/export-button)))
    :breadcrumbs [(tr "Contacts")]
    :page-title-buttons (when (authz/user-has-permission? viewer contact.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default default-page-size} :int]
   [:q :string]])

(defn handler [& {:keys [::z/context query-params uri viewer] :as request}]
  (let [{:keys [db timezone]} context
        {:keys [page page-size q]} (params/decode Params query-params)
        offset (* page-size (- page 1))

        ;; Parse search query
        ast (search.i/parse q)

        ;; Base statement
        base-stmt {:select [:c.*]
                   :from [[:contact :c]]}

        ;; Compile search query (adds WHERE clause)
        stmt (search.i/compile-query :contact ast base-stmt {:timezone timezone})

        ;; Execute queries
        total (db.i/count-bounded db stmt)
        view (list-view/resolve request :contact (table-columns timezone))
        rows (db.i/execute-bounded! db (-> stmt
                                           (list-query/with-columns (:columns view) (:sort view))
                                           (assoc :limit page-size
                                                  :offset offset
                                                  :order-by (list-query/order-by
                                                              (:sort view)
                                                              {:relevance (search.i/relevance-order :contact ast)
                                                               :default [[:c.name :asc]]
                                                               :tiebreak [:c.id :asc]}))))
        table-opts (list-view/table-opts view uri q)]

    (cond
      ;; The combobox asks for its rows as markup, so what an option looks
      ;; like is decided here with the rest of the UI.
      (some? (get query-params "options"))
      (html/render-partial
        (ui.combobox/options-fragment
          :total total
          :items (for [contact rows]
                   {:id (:contact/id contact)
                    :text (contact.name/label contact)
                    :content (ui.combobox/option-content
                               :icon (lucide/contact-round)
                               :title (:contact/name contact)
                               :meta (:contact/business contact))})))
      ;; Infinite scroll: the sentinel asks for the next page's rows alone and
      ;; swaps itself out for them.
      (some? (get query-params "rows"))
      (html/render-partial
        (index-rows :rows rows
                    :page-num page
                    :page-size page-size
                    :total total
                    :table-opts table-opts
                    :href (list-view/href view uri q)))

      :else
      (list-view/respond
        view
        (render :viewer viewer
                :field-options (search.i/field-options :contact)
                :href (list-view/href view uri q :page page)
                :rows rows
                :page-num page
                :page-size page-size
                :search-query q
                :table-opts table-opts
                :total total)
        uri q))))
