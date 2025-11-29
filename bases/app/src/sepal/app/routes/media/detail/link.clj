(ns sepal.app.routes.media.detail.link
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.media.interface :as media.i]
            [zodiac.core :as z]))

(def resource-types
  [{:label "Accession"
    :value "accession"}
   {:label "Material"
    :value "material"}
   {:label "Taxon"
    :value "taxon"}
   {:label "Location"
    :value "location"}])

(defn taxon-field [& {:keys [taxon-name name id taxon-id]}]
  (let [url (z/url-for taxon.routes/index)]
    [:select {:x-taxon-field (json/js {:url url})
              :id (or id name)
              :class "input input-bordered input-sm"
              :name name
              :autocomplete "off"
              :required true}
     (when taxon-id
       [:option {:value taxon-id}
        taxon-name])]))

(defn accession-field [& {:keys [accession-name name id accession-id]}]
  (let [url (z/url-for taxon.routes/index)]
    [:select {:x-accession-field (json/js {:url url})
              :id (or id name)
              :class "input input-bordered input-sm"
              :name name
              :autocomplete "off"
              :required true}
     (when accession-id
       [:option {:value accession-id}
        accession-name])]))

(defn location-field [& {:keys [location-name name id location-id]}]
  ;; TODO: Are these routes correct?
  (let [url (z/url-for taxon.routes/index)]
    [:select {:x-location-field (json/js {:url url})
              :id (or id name)
              :class "input input-bordered input-sm"
              :name name
              :autocomplete "off"
              :required true}
     (when location-id
       [:option {:value location-id}
        location-name])]))

(defn material-field [& {:keys [material-name name id material-id]}]
  (let [url (z/url-for material.routes/index)]
    [:select {:x-material-field (json/js {:url url})
              :id (or id name)
              :class "select select-bordered select-md w-full max-w-xs px-2"
              :name name
              :autocomplete "off"
              :required true}
     (when material-id
       [:option {:value material-id}
        material-name])]))

(defn media-link-form [& {:keys [media]}]
  (form/form
    {:class "flex flex-row gap-2 items-center"
     :hx-post (z/url-for media.routes/detail-link {:id (:media/id media)})
     :hx-target "#media-link-root"}
    [(form/anti-forgery-field)
     (form/field :label "Resource type"
                 :name "resource-type"
                 :input [:select {:name "resource-type"
                                  :class "select select-bordered select-sm w-full max-w-xs leading-4"
                                  :autocomplete "off"
                                  :id "resource-type"
                                  :x-model "resourceType"
                                  :required true
                                 ;; :value (:rank values)
                                  }
                         [[:option {:value ""} ""]
                          (for [rt resource-types]
                            [:option {:value  (:value rt)}
                             (:label rt)])]])
     [:div {:x-show "resourceType"
            :class "flex flex-row gap-2 items-end flex-grow"}
      (form/field :label "Resource"
                  :name "resource-id"
                  :input [:<>
                          [:template {:x-if "resourceType === 'accession'"}
                           (accession-field :name "resource-id")]
                          [:template {:x-if "resourceType === 'location'"}
                           (location-field :name "resource-id")]
                          [:template {:x-if "resourceType === 'material'"}
                           (material-field :name "resource-id")]
                          [:template {:x-if "resourceType === 'taxon'"}
                           (taxon-field :name "resource-id")]])

      (form/submit-button {:class "btn btn-sm btn-primary mb-4"}  "Save")
      [:button {:type "button"
                :class "btn btn-sm mb-4"
                :x-on:click "editLink=false"}
       "Cancel"]]]))

;; (ns-unmap *ns* 'link-text)

(defmulti link-text
  (fn [_db link]
    (:media-link/resource-type link)))

(defmethod link-text "accession"
  [db link]
  (->> {:select [[[:concat :a.code " (" :t.name ")"] :text]]
        :from [[:media-link :ml]]
        :join [[:accession :a]
               [:= :a.id (:media-link/resource-id link)]
               [:taxon :t]
               [:= :t.id :a.taxon-id]]
        :where [:= :ml.id (:media-link/id link)]}
       (db.i/execute-one! db)
       :text))

(defmethod link-text "location"
  [db link]
  (->> {:select [[[:concat :l.name " (" :l.code ")"] :text]]
        :from [[:media-link :ml]]
        :join [[:location :l]
               [:= :l.id (:media-link/resource-id link)]]
        :where [:= :ml.id (:media-link/id link)]}
       (db.i/execute-one! db)
       :text))

(defmethod link-text "material"
  [db link]
  (->> {:select [[[:concat :a.code "." :m.code " (" :t.name ")"] :text]]
        :from [[:media-link :ml]]
        :join [[:material :m]
               [:= :m.id (:media-link/resource-id link)]
               [:accession :a]
               [:= :a.id :m.accession_id]
               [:taxon :t]
               [:= :t.id :a.taxon-id]]
        :where [:= :ml.id (:media-link/id link)]}
       (db.i/execute-one! db)
       :text))

(defmethod link-text "taxon"
  [db link]
  (->> {:select [[[:concat  :t.name] :text]]
        :from [[:media-link :ml]]
        :join [[:taxon :t]
               [:= :t.id (:media-link/resource-id link)]]
        :where [:= :ml.id (:media-link/id link)]}
       (db.i/execute-one! db)
       :text))

(defn link-anchor [& {:keys [db link]}]
  (let [text (link-text db link)
        url (case (:media-link/resource-type link)
              "accession" (z/url-for accession.routes/detail {:id (:media-link/resource-id link)})
              "location" (z/url-for location.routes/detail {:id (:media-link/resource-id link)})
              "material" (z/url-for material.routes/detail {:id (:media-link/resource-id link)})
              "taxon" (z/url-for taxon.routes/detail {:id (:media-link/resource-id link)}))]
    [:a {:href url} text]))

(defn delete-button [& {:keys [media]}]
  [:btn {:href "#"
         :class "btn btn-sm btn-square btn-outline btn-error *:hover:text-white"
         :hx-confirm "Are you sure you want to remove this link?"
         :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
         :hx-delete (z/url-for media.routes/detail-link {:id (:media/id media)})
         :hx-target "#media-link-root"
         :alt "Delete"}
   (heroicons/outline-trash :class "size-4")])

(defn render [& {:keys [anchor link media]}]
  (-> [:div#media-link-root {:x-data (json/js {:editLink false
                                               :resourceType (:media-link/resource-type link)})}
       [:template {:x-if "!editLink"}
        (if link
          [:div {:class "flex flex-row items-center gap-4 my-2"}
           anchor
           (delete-button :media media)]
          [:btn {:href "#"
                 :class "btn btn-sm btn-square my-2"
                 :x-on:click "editLink=true"
                 :alt "Link"}
           (heroicons/outline-link)])]
       [:div {:x-show "editLink"} ;;:template {:x-if "editLink"}
        (media-link-form :link link
                         :media media)]]
      (html/render-partial)))

(defn handler [& {:keys [::z/context params request-method] :as _request}]
  ;; TODO: create an activity
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [{:keys [resource-id resource-type]} params
            result (media.i/link! db (:media/id resource) resource-id resource-type)]
        (if-not (error.i/error? result)
          (render :link result
                  :media resource)
          ;; TODO: render an error
          (flash/error {} "Error: Could not link resource")))
      :delete
      (let [result (media.i/unlink! db (:media/id resource))]
        (tap> (str "result: " result))
        (if-not (error.i/error? result)
          (render :media resource)
          ;; TODO: render an error
          (flash/error {} "Error: Could not unlink resource")))

      :get
      (let [link (media.i/get-link db (:media/id resource))
            anchor (link-anchor :db db :link link)]
        (render :anchor anchor
                :link link
                :media resource)))))
