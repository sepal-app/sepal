(ns sepal.app.routes.location.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [sepal.location.interface.permission :as location.perm]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn create-button []
  [:a {:class "btn btn-primary"
       :href (z/url-for location.routes/new)}
   "Create"])

(defn row-attrs
  "Generate HTMX attributes for clickable table rows that open the preview panel."
  [location]
  (let [id (:location/id location)]
    {:class (html/attr "cursor-pointer" "hover:bg-base-200")
     :hx-get (z/url-for location.routes/panel {:id id})
     :hx-target "#preview-panel-content"
     :hx-swap "innerHTML"
     :hx-push-url "false"
     :x-on:click "panelOpen = true"
     :x-bind:class (str "selectedId === " id " ? 'bg-base-200' : ''")}))

(defn table-columns []
  [{:name "Name"
    :cell (fn [l] [:a {:href (z/url-for location.routes/detail
                                        {:id (:location/id l)})
                       :class "spl-link"
                       :x-on:click.stop ""}
                   (:location/name l)])}
   {:name "Code"
    :cell :location/code}
   {:name "Description"
    :cell :location/description}])

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
    :breadcrumbs ["Locations"]
    :page-title-buttons (when (authz/user-has-permission? viewer location.perm/create)
                          (create-button))))

(defn handler [& {:keys [::z/context headers query-params uri viewer]}]
  (let [{:keys [db]} context
        ;; TODO: validate page and page size
        {:strs [page page-size q]
         :or {page-size default-page-size}} query-params
        page-num (or (when page (Integer/parseInt page)) 1)
        offset (* page-size (- page-num 1))
        stmt {:select [:l.*]
              :from [[:location :l]]
              :where (if q
                       [:or
                        [:like :name (format "%%%s%%" q)]
                        [:like :code (format "%%%s%%" q)]]
                       :true)}
        total (db.i/count db stmt)
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      :order-by [:name]))]

    (if (= (get headers "accept") "application/json")
      (json/json-response (for [location rows]
                            {:name (:location/name location)
                             :text (format "%s (%s)"
                                           (:location/code location)
                                           (:location/name location))
                             :id (:location/id location)
                             :code (:location/code location)
                             :description (:location/description location)}))
      (render :viewer viewer
              :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :rows rows
              :page-num page-num
              :page-size page-size
              :total total))))
