(ns sepal.app.routes.accession.index
  (:require [lambdaisland.uri :as uri]
            [sepal.accession.interface.permission :as accession.perm]
            [sepal.app.authorization :as authz]
            [sepal.app.json :as json]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.contact.interface :as contact.i]
            [sepal.database.interface :as db.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn create-button []
  [:a {:class "btn btn-primary"
       :href (z/url-for accession.routes/new)}
   "Create"])

(defn- row-attrs
  "Generate attributes for a table row to enable panel preview."
  [row]
  (let [id (:accession/id row)
        panel-url (z/url-for accession.routes/panel {:id id})]
    {:class "hover:bg-base-200 cursor-pointer"
     :x-bind:class (str "selectedId === " id " ? 'bg-base-200' : ''")
     :x-on:click (str "selectedId = " id "; panelOpen = true")
     :hx-get panel-url
     :hx-target (str "#" pages.list/panel-container-id)
     :hx-swap "innerHTML"
     :hx-push-url "false"}))

(defn table-columns []
  [{:name "Code"
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
                     (:taxon/name row)])}])

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

(defn render [& {:keys [filters viewer href page page-size rows supplier taxon total]}]
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
                   taxon (conj [:a {:href (z/url-for taxon.routes/index)}
                                "Taxa"]
                               [:a {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})
                                    :class "italic"}
                                (:taxon/name taxon)])
                   supplier (conj [:a {:href (z/url-for contact.routes/index)}
                                   "Contacts"]
                                  [:a {:href (z/url-for contact.routes/detail {:id (:contact/id supplier)})}
                                   (:contact/name supplier)])
                   :always
                   (conj "Accessions"))
    :page-title-buttons (when (authz/user-has-permission? viewer accession.perm/create)
                          (create-button))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]
   [:supplier-contact-id {:min 0} :int]
   [:taxon-id {:min 0} :int]])

(defn handler [& {:keys [::z/context headers query-params uri viewer]}]
  (let [{:keys [db]} context
        {:keys [page page-size q supplier-contact-id taxon-id]} (params/decode Params query-params)
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
                        [:= :t.id taxon-id])
                      (when supplier-contact-id
                        [:= :a.supplier_contact_id supplier-contact-id])]}
        total (db.i/count db stmt)
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      :order-by [:code]))
        taxon (when taxon-id
                (taxon.i/get-by-id db taxon-id))
        supplier (when supplier-contact-id
                   (contact.i/get-by-id db supplier-contact-id))
        filters (cond-> []
                  taxon (conj {:label "Taxon"
                               :value (:taxon/name taxon)
                               :clear-href (uri/uri-str
                                             {:path uri
                                              :query (uri/map->query-string
                                                       (dissoc query-params "taxon-id" "page"))})})
                  supplier (conj {:label "Supplier"
                                  :value (:contact/name supplier)
                                  :clear-href (uri/uri-str
                                                {:path uri
                                                 :query (uri/map->query-string
                                                          (dissoc query-params "supplier-contact-id" "page"))})}))]

    (if (= (get headers "accept") "application/json")
      ;; TODO: json response
      (json/json-response (for [row rows]
                            {:text (format "%s (%s)"
                                           (:accession/code row)
                                           (:taxon/name row))
                             :code (:accession/code row)
                             :id (:accession/id row)}))
      (render :viewer viewer
              :filters filters
              :href (uri/uri-str {:path uri
                                  :query (uri/map->query-string query-params)})
              :rows rows
              :page page
              :page-size page-size
              :supplier supplier
              :taxon taxon
              :total total))))
