(ns sepal.app.routes.accession.index
  (:require [lambdaisland.uri :as uri]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn create-button [& {:keys []}]
  [:a {:class "btn btn-primary"
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

(defn render [& {:keys [href page page-size rows taxon total]}]
  (ui.page/page
    :content (pages.list/page-content
               :content (table :href href
                               :page page
                               :page-size page-size
                               :rows rows
                               :total total)
               :table-actions (pages.list/search-field (-> href uri/query-map :q)))
    :breadcrumbs (cond-> []
                   taxon (conj [:a {:href (z/url-for taxon.routes/index)}
                                "Taxa"]
                               [:a {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                                    :class "italic"}
                                (:taxon/name taxon)])
                   :always
                   (conj "Accessions"))
    :page-title-buttons (create-button)))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]
   [:taxon-id  {:min 0} :int]])

(defn handler [& {:keys [::z/context headers query-params uri]}]
  (let [{:keys [db]} context
        {:keys [page page-size q taxon-id]} (params/decode Params query-params)
        offset (* page-size (- page 1))
        stmt {:select [:*]
              :from [[:accession :a]]
              :join [[:taxon :t]
                     [:= :t.id :a.taxon_id]]
              :where [:and
                      (if q
                        [:like :a.code (format "%%%s%%" q)]
                        :true)
                      (when taxon-id
                        [:= :t.id taxon-id])]}
        total (db.i/count db stmt)
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      :order-by [:code]))
        taxon (when taxon-id
                (taxon.i/get-by-id db taxon-id))]

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
              :taxon taxon
              :total total))))
