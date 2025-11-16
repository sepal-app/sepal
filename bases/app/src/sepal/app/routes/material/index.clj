(ns sepal.app.routes.material.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.json :as json]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn create-button [& {:keys []}]
  [:a {:class ["inline-flex" "items-center" "justify-center" "rounded-md"
               "border" "border-transparent" "bg-green-700" "px-4" "py-2"
               "text-sm" "font-medium" "text-white" "shadow-sm" "hover:bg-green-700"
               "focus:outline-none" "focus:ring-2" "focus:ring-grenn-500"
               "focus:ring-offset-2" "sm:w-auto"]
       :href (z/url-for material.routes/new)}
   "Create"])

(defn table-columns []
  [{:name "Code"
    :cell (fn [row] [:a {:href (z/url-for material.routes/detail
                                          {:id (:material/id row)})
                         :class "spl-link"}
                     (:material/code row)])}
   {:name "Accession"
    :cell (fn [row] [:a {:href (z/url-for accession.routes/detail
                                          {:id (:accession/id row)})
                         :class "spl-link"}
                     (:accession/code row)])}
   {:name "Taxon"
    ;; TODO: Show the taxon parent on hover
    :cell (fn [row] [:a {:href (z/url-for taxon.routes/detail
                                          {:id (:taxon/id row)})
                         :class "spl-link"}
                     (:taxon/name row)])}
   {:name "Location"
    ;; TODO: Show the full location name on hover
    :cell (fn [row] [:a {:href (z/url-for location.routes/detail
                                          {:id (:location/id row)})
                         :class "spl-link"}
                     (:location/code row)])}])

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
                     :page-title "Materials"
                     :page-title-buttons (create-button)
                     :table-actions (pages.list/search-field (-> href uri/query-map :q))))

(defn handler [& {:keys [::z/context headers query-params uri]}]
  (let [{:keys [db]} context
        ;; TODO: validate page and page size
        {:strs [page page-size q]
         :or {page-size default-page-size}} query-params
        page-num (or (when page (Integer/parseInt page)) 1)
        offset (* page-size (- page-num 1))
        stmt {:select [:*]
              :from [[:material :m]]
              :join [[:accession :a]
                     [:= :a.id :m.accession_id]
                     [:taxon :t]
                     [:= :t.id :a.taxon_id]
                     [:location :l]
                     [:= :l.id :m.location_id]]
              :where (if q
                       [:or
                        [:like :m.code (format "%%%s%%" q)]
                        [:like :a.code (format "%%%s%%" q)]]
                       :true)}
        total (db.i/count db stmt)
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      ;; TODO: We either need to add the
                                      ;; timestamp audit columns to our models
                                      ;; or join against the activity feed
                                      ;; :order-by [[:m.created_at :desc]]
                                      ))]

    (if (= (get headers "accept") "application/json")
      (json/json-response (for [material rows]
                            {:code (:material/code material)
                             :id (:material/id material)
                             :text (format "%s.%s (%s)"
                                           (:accession/code material)
                                           (:material/code material)
                                           (:taxon/name material))
                             :accession-id (:material/accession-id material)}))
      (render :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :rows rows
              :page-num page-num
              :page-size page-size
              :total total))))
