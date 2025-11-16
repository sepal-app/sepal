(ns sepal.app.routes.accession.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(defn create-button [& {:keys []}]
  [:a {:class ["inline-flex" "items-center" "justify-center" "rounded-md"
               "border" "border-transparent" "bg-green-700" "px-4" "py-2"
               "text-sm" "font-medium" "text-white" "shadow-sm" "hover:bg-green-700"
               "focus:outline-none" "focus:ring-2" "focus:ring-grenn-500"
               "focus:ring-offset-2" "sm:w-auto"]
       :href (z/url-for accession.routes/new)}
   "Create"])

(defn table-columns []
  [{:name "Code"
    :cell (fn [row] [:a {:href (z/url-for accession.routes/detail
                                          {:id (:accession/id row)})
                         :class "spl-link"}
                     (:accession/code row)])}
   {:name "Taxon"
    :cell (fn [row] [:a {:href (z/url-for taxon.routes/detail
                                          {:id (:taxon/id row)})
                         :class "spl-link"}
                     (:taxon/name row)])}])

(defn table [& {:keys [rows page href page-size total]}]
  [:div {:class "w-full"}
   (table/card-table
     (table/table :columns (table-columns)
                  :rows rows)
     (table/paginator :current-page page
                      :href href
                      :page-size page-size
                      :total total))])

(defn render [& {:keys [href page page-size rows total]}]
  (pages.list/render :content (table :href href
                                     :page page
                                     :page-size page-size
                                     :rows rows
                                     :total total)
                     :page-title "Accessions"
                     :page-title-buttons (create-button)
                     :table-actions (pages.list/search-field (-> href uri/query-map :q))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]])

(defn handler [& {:keys [::z/context headers query-params uri]}]
  (let [{:keys [db]} context
        {:keys [page page-size q]} (params/decode Params query-params)
        offset (* page-size (- page 1))
        stmt {:select [:*]
              :from [[:accession :a]]
              :join [[:taxon :t]
                     [:= :t.id :a.taxon_id]]
              :where [:and
                      (if q
                        [:like :a.code (format "%%%s%%" q)]
                        :true)]}
        total (db.i/count db stmt)
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      :order-by [:code]))]

    (if (= (get headers "accept") "application/json")
      ;; TODO: json response
      (json/json-response (for [row rows]
                            {:text (format "%s (%s)"
                                           (:accession/code row)
                                           (:taxon/name row))
                             :code (:accession/code row)
                             :id (:accession/id row)}))
      (render :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :rows rows
              :page page
              :page-size page-size
              :total total))))
