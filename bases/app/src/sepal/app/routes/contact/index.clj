(ns sepal.app.routes.contact.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.contact.interface.permission :as contact.perm]
            [sepal.database.interface :as db.i]
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
    {:class (html/attr "cursor-pointer" "hover:bg-base-200")
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

(defn render [& {:keys [viewer href page-num page-size rows total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content (table :href href
                               :page-num page-num
                               :page-size page-size
                               :rows rows
                               :total total)
               :table-actions (pages.list/search-field (-> href uri/query-map :q)))
    :breadcrumbs ["Contacts"]
    :page-title-buttons (when (authz/user-has-permission? viewer contact.perm/create)
                          (create-button))))

(defn handler [& {:keys [::z/context headers query-params uri viewer]}]
  (let [{:keys [db]} context
        ;; TODO: validate page and page size
        {:strs [page page-size q]
         :or {page-size default-page-size}} query-params
        page-num (or (when page (Integer/parseInt page)) 1)
        offset (* page-size (- page-num 1))
        stmt {:select [:l.*]
              :from [[:contact :l]]
              :where (if q
                       [:or
                        [:like :name (format "%%%s%%" q)]
                        [:like :business (format "%%%s%%" q)]]
                       :true)}
        total (db.i/count db stmt)
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      :order-by [:name]))]

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
              :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :rows rows
              :page-num page-num
              :page-size page-size
              :total total))))
