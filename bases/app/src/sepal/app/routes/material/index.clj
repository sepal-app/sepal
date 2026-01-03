(ns sepal.app.routes.material.index
  (:require [lambdaisland.uri :as uri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.authorization :as authz]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.material.interface.permission :as material.perm]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(def default-page-size 25)

(defn create-button []
  [:a {:class "btn btn-primary"
       :href (z/url-for material.routes/new)}
   "Create"])

(defn- row-attrs
  "Generate attributes for a table row to enable panel preview."
  [row]
  (let [id (:material/id row)
        panel-url (z/url-for material.routes/panel {:id id})]
    {:class "hover:bg-base-200 cursor-pointer"
     :x-bind:class (str "selectedId === " id " ? 'bg-base-200' : ''")
     :x-on:click (str "selectedId = " id "; panelOpen = true")
     :hx-get panel-url
     :hx-target (str "#" pages.list/panel-container-id)
     :hx-swap "innerHTML"
     :hx-push-url "false"}))

(defn table-columns []
  [{:name "Code"
    :cell (fn [row] [:a {:href (z/url-for material.routes/detail
                                          {:id (:material/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:material/code row)])}
   {:name "Accession"
    :cell (fn [row] [:a {:href (z/url-for accession.routes/detail
                                          {:id (:accession/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:accession/code row)])}
   {:name "Taxon"
    :cell (fn [row] [:a {:href (z/url-for taxon.routes/detail
                                          {:id (:taxon/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:taxon/name row)])}
   {:name "Location"
    :cell (fn [row] [:a {:href (z/url-for location.routes/detail
                                          {:id (:location/id row)})
                         :class "spl-link"
                         :x-on:click.stop ""}
                     (:location/code row)])}])

(defn table [& {:keys [rows page href page-size total]}]
  [:div {:class "w-full"}
   (table/card-table
     (table/table :columns (table-columns)
                  :rows rows
                  :row-attrs row-attrs)
     (table/paginator :current-page page
                      :href href
                      :page-size page-size
                      :total total))])

(defn render [& {:keys [filters viewer accession href location page page-size rows taxon total]}]
  (ui.page/page
    :content (pages.list/page-content-with-panel
               :content (table :href href
                               :page page
                               :page-size page-size
                               :rows rows
                               :total total)
               :filters filters
               :table-actions (pages.list/search-field (-> href uri/query-map :q)))
    :breadcrumbs (cond-> []
                   taxon (conj [:a {:href (z/url-for taxon.routes/index)} "Taxa"]
                               [:a {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                                    :class "italic"}
                                (:taxon/name taxon)])
                   accession (conj [:a {:href (z/url-for accession.routes/index)} "Accessions"]
                                   [:a {:href (z/url-for accession.routes/detail {:id (:accession/id accession)})
                                        :class "italic"}
                                    (:accession/code accession)])
                   location (conj [:a {:href (z/url-for location.routes/index)} "Locations"]
                                  [:a {:href (z/url-for location.routes/detail {:id (:location/id location)})}
                                   (:location/name location)])
                   :always (conj "Materials"))
    :page-title-buttons (when (authz/user-has-permission? viewer material.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]
   [:accession-id {:min 0} :int]
   [:location-id {:min 0} :int]
   [:taxon-id {:min 0} :int]])

(defn handler [& {:keys [::z/context headers query-params uri viewer]}]
  (let [{:keys [db]} context
        {:keys [accession-id location-id page page-size q taxon-id]} (params/decode Params query-params)
        offset (* page-size (- page 1))
        stmt {:select [:*]
              :from [[:material :m]]
              :join [[:accession :a]
                     [:= :a.id :m.accession_id]
                     [:taxon :t]
                     [:= :t.id :a.taxon_id]
                     [:location :l]
                     [:= :l.id :m.location_id]]
              :where [:and
                      (if q
                        [:or
                         [:like :m.code (format "%%%s%%" q)]
                         [:like :a.code (format "%%%s%%" q)]]
                        :true)
                      (when taxon-id
                        [:= :t.id taxon-id])
                      (when (and accession-id (not taxon-id))
                        [:= :a.id accession-id])
                      (when location-id
                        [:= :l.id location-id])]}
        total (db.i/count db stmt)
        taxon (when taxon-id
                (taxon.i/get-by-id db taxon-id))
        accession (when (and accession-id (not taxon-id))
                    (accession.i/get-by-id db accession-id))
        location (when location-id
                   (location.i/get-by-id db location-id))
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      ;; TODO: We either need to add the
                                      ;; timestamp audit columns to our models
                                      ;; or join against the activity feed
                                      ;; :order-by [[:m.created_at :desc]]
                                      ))
        filters (cond-> []
                  taxon (conj {:label "Taxon"
                               :value (:taxon/name taxon)
                               :clear-href (uri/uri-str
                                            {:path uri
                                             :query (uri/map->query-string
                                                     (dissoc query-params "taxon-id" "page"))})})
                  accession (conj {:label "Accession"
                                   :value (:accession/code accession)
                                   :clear-href (uri/uri-str
                                                {:path uri
                                                 :query (uri/map->query-string
                                                         (dissoc query-params "accession-id" "page"))})})
                  location (conj {:label "Location"
                                  :value (:location/name location)
                                  :clear-href (uri/uri-str
                                               {:path uri
                                                :query (uri/map->query-string
                                                        (dissoc query-params "location-id" "page"))})}))]

    (if (= (get headers "accept") "application/json")
      (json/json-response (for [material rows]
                            {:code (:material/code material)
                             :id (:material/id material)
                             :text (format "%s.%s (%s)"
                                           (:accession/code material)
                                           (:material/code material)
                                           (:taxon/name material))
                             :accession-id (:material/accession-id material)}))
      (render :viewer viewer
              :accession accession
              :filters filters
              :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :location location
              :rows rows
              :page page
              :page-size page-size
              :taxon taxon
              :total total))))
