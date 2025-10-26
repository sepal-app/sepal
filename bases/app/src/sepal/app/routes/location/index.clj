(ns sepal.app.routes.location.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn search-field [q]
  [:div {:class "flex flex-row"}
   [:input {:name "q"
            :class "input input-md w-fill max-w-xs bg-white w-96"
            :type "search"
            :value q
            :placeholder "Search..."}]
   [:button
    {:type "button",
     :class (html/attr "inline-flex" "items-center" "mx-2" "px-2.5" "py-1.5" "border"
                       "border-gray-300" "shadow-sm" "text-xs" "font-medium" "rounded"
                       "text-gray-700" "bg-white" "hover:bg-gray-50" "focus:outline-none"
                       "focus:ring-2" "focus:ring-offset-2" "focus:ring-indigo-500")
     :onclick "document.getElementById('q').value = null; this.form.submit()"}
    (heroicons/outline-x :size 20)]])

(defn create-button [& {:keys []}]
  [:a {:class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                         "border" "border-transparent" "bg-green-700" "px-4" "py-2"
                         "text-sm" "font-medium" "text-white" "shadow-sm" "hover:bg-green-700"
                         "focus:outline-none" "focus:ring-2" "focus:ring-grenn-500"
                         "focus:ring-offset-2" "sm:w-auto")
       :href (z/url-for location.routes/new)}
   "Create"])

(defn table-columns []
  [{:name "Name"
    :cell (fn [l] [:a {:href (z/url-for location.routes/detail
                                        {:id (:location/id l)})
                       :class "spl-link"}
                   (:location/name l)])}
   {:name "Code"
    :cell :location/code}
   {:name "Description"
    :cell :location/description}])

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
  (pages.list/render :content (table :href href
                                     :page-num page-num
                                     :page-size page-size
                                     :rows rows
                                     :total total)
                     :page-title "Locations"
                     :page-title-buttons (create-button)
                     :table-actions (search-field (-> href uri/query-map :q))))

(defn handler [& {:keys [::z/context headers query-params uri]}]
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
      (render :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :rows rows
              :page-num page-num
              :page-size page-size
              :total total))))
