(ns sepal.app.routes.contact.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.authorization :as authz]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.contact.export :as export]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.export :as ui.export]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.query-builder :as query-builder]
            [sepal.app.ui.table :as table]
            [sepal.contact.interface.permission :as contact.perm]
            [sepal.contact.interface.search]
            [sepal.database.interface :as db.i]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn create-button []
  [:a {:class "btn btn-primary"
       :href (z/url-for contact.routes/new)}
   "Create"])

(defn row-attrs
  "Generate HTMX attributes for clickable table rows that open the preview panel."
  [contact]
  (let [id (:contact/id contact)]
    {:class "cursor-pointer hover:bg-base-200/50"
     :hx-get (z/url-for contact.routes/panel {:id id})
     :hx-target "#preview-panel-content"
     :hx-swap "innerHTML"
     :hx-push-url "false"
     :x-on:click "panelOpen = true"
     :x-bind:class (str "selectedId === " id " ? 'bg-base-200' : ''")}))

(defn table-columns []
  [{:name "Name"
    :cell (fn [l] [:a {:href (z/url-for contact.routes/detail
                                        {:id (:contact/id l)})
                       :class "spl-link"
                       :x-on:click.stop ""}
                   (:contact/name l)])}
   {:name "Business"
    :cell :contact/business}
   {:name "Email"
    :cell :contact/email}
   {:name "Phone"
    :cell :contact/phone}])

(defn table [& {:keys [rows page-num href page-size total]}]
  [:div {:class "w-full"}
   (table/card-table
     (table/table :columns (table-columns)
                  :rows rows
                  :row-attrs row-attrs)
     (table/paginator :current-page page-num
                      :href href
                      :page-size page-size
                      :total total))])

(defn render [& {:keys [field-options viewer href page-num page-size rows search-query total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content [:div
                         (table :href href
                                :page-num page-num
                                :page-size page-size
                                :rows rows
                                :total total)
                         (ui.export/export-modal
                           :total total
                           :search-query search-query
                           :export-action (z/url-for contact.routes/export)
                           :options export/export-options)]
               :table-actions [:div {:class "flex items-center justify-between w-full"}
                               (query-builder/search-field-with-builder
                                 :q search-query
                                 :fields field-options)
                               (ui.export/export-button)])
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

    (if (= (get headers "accept") "application/json")
      (json/json-response (for [contact rows]
                            {:name (:contact/name contact)
                             :text (format "%s (%s)"
                                           (:contact/business contact)
                                           (:contact/name contact))
                             :id (:contact/id contact)
                             :business (:contact/business contact)
                             :description (:contact/description contact)}))
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
