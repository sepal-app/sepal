(ns sepal.app.routes.contact.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.json :as json]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn create-button [& {:keys []}]
  [:a {:class "btn btn-primary"
       :href (z/url-for contact.routes/new)}
   "Create"])

(defn table-columns []
  [{:name "Name"
    :cell (fn [l] [:a {:href (z/url-for contact.routes/detail
                                        {:id (:contact/id l)})
                       :class "spl-link"}
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
                  :rows rows)
     (table/paginator :current-page page-num
                      :href href
                      :page-size page-size
                      :total total))])

(defn render [& {:keys [href page-num page-size rows total]}]
  (ui.page/page
    :content (pages.list/page-content
               :content (table :href href
                               :page-num page-num
                               :page-size page-size
                               :rows rows
                               :total total)

               :table-actions (pages.list/search-field (-> href uri/query-map :q)))
    :breadcrumbs ["Contacts"]
    :page-title-buttons (create-button)))

(defn handler [& {:keys [::z/context headers query-params uri]}]
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
      (render :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :rows rows
              :page-num page-num
              :page-size page-size
              :total total))))
