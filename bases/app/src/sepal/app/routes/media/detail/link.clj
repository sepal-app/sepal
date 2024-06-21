(ns sepal.app.routes.media.detail.link
  (:require [reitit.core :as r]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.error.interface :as error.i]
            [sepal.media.interface :as media.i]))

(def resource-types
  [{:label "Accession"
    :value "accession"}
   ;; TODO: Need to add a MaterialField first
   #_{:label "Material"
      :value "material"}
   {:label "Taxon"
    :value "taxon"}
   {:label "Location"
    :value "location"}])

(defn taxon-field [& {:keys [org taxon-name router name id taxon-id]}]
  (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
    [:select {:x-taxon-field (json/js {:url url})
              :x-validate.required true
              :id (or id name)
              :class "input input-bordered input-sm"
              :name name
              :autocomplete "off"}
     (when taxon-id
       [:option {:value taxon-id}
        taxon-name])]))

(defn accession-field [& {:keys [org accession-name router name id accession-id]}]
  (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
    [:select {:x-accession-field (json/js {:url url})
              :x-validate.required true
              :id (or id name)
              :class "input input-bordered input-sm"
              :name name
              :autocomplete "off"}
     (when accession-id
       [:option {:value accession-id}
        accession-name])]))

(defn location-field [& {:keys [org location-name router name id location-id]}]
  (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
    [:select {:x-location-field (json/js {:url url})
              :x-validate.required true
              :id (or id name)
              :class "input input-bordered input-sm"
              :name name
              :autocomplete "off"}
     (when location-id
       [:option {:value location-id}
        location-name])]))

(defn material-field [& {:keys [org material-name router name id material-id]}]
  (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
    [:select {:x-material-field (json/js {:url url})
              :x-validate.required true
              :id (or id name)
              :class "input input-bordered input-sm"
              :name name
              :autocomplete "off"}
     (when material-id
       [:option {:value material-id}
        material-name])]))

(defn media-link-form [& {:keys [media org router]}]
  (form/form
   {:class "flex flex-row gap-2 items-center"
    :hx-post (url-for router :media/detail.link {:id (:media/id media)})
    :hx-target "#media-link-root"}
   [:<>
    (form/anti-forgery-field)
    (form/field :label "Resource type"
                :name "resource-type"
                :input [:select {:name "resource-type"
                                 :class "select select-bordered select-sm w-full max-w-xs"
                                 :autocomplete "off"
                                 :id "resource-type"
                                 :x-validate.required true
                                 :x-model "resourceType"
                                 ;; :value (:rank values)
                                 }
                        [:<>
                         [:option {:value ""} ""]
                         (for [rt resource-types]
                           [:option {:value  (:value rt)}
                            (:label rt)])]])
    [:div {:x-show "resourceType"
           :class "flex flex-row gap-2 items-end flex-grow"}
     (form/field :label "Resource"
                 :name "resource-id"
                 :input [:<>
                         [:template {:x-if "resourceType === 'accession'"}
                          (accession-field :org org
                                           :router router
                                           :name "resource-id")]
                         [:template {:x-if "resourceType === 'location'"}
                          (location-field :org org
                                          :router router
                                          :name "resource-id")]
                         [:template {:x-if "resourceType === 'material'"}
                          (material-field :org org
                                          :router router
                                          :name "resource-id")]
                         [:template {:x-if "resourceType === 'taxon'"}
                          (taxon-field :org org
                                       :router router
                                       :name "resource-id")]])

     [:button {:type "button"
               :class "btn btn-sm btn-secondary mb-4"
               :x-on:click "editLink=false"}
      "Cancel"]
     (form/button {:class "btn btn-sm btn-primary mb-4"}  "Save")]]))

(defn link-anchor [& {:keys [router link]}]
  (case (:media-link/resource-type link)
    "accession"
    [:a {:href (url-for router :accession/detail {:id (:media-link/resource-id link)})}
     (-> link :media-link/resource :accession/code)]
    "location"
    [:a {:href (url-for router :location/detail {:id (:media-link/resource-id link)})}
     (-> link :media-link/resource :location/name)]
    "material"
    [:a {:href (url-for router :material/detail {:id (:media-link/resource-id link)})}
     (-> link :media-link/resource :material/code)]
    "taxon"
    [:a {:href (url-for router :taxon/detail {:id (:media-link/resource-id link)})}
     (-> link :media-link/resource :taxon/name)]))

(defn delete-button [& {:keys [media router]}]
  [:btn {:href "#"
         :class "btn btn-sm btn-square btn-outline btn-error *:hover:text-white"
         :hx-confirm "Are you sure you want to remove this link?"
         :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
         :hx-delete (url-for router :media/detail.link {:id (:media/id media)})
         :hx-target "#media-link-root"
         :alt "Delete"}
   (heroicons/outline-trash :class "size-4")])

(defn render [& {:keys [link media org router]}]
  (-> [:div#media-link-root {:x-data (json/js {:editLink false
                                               :resourceType (:media-link/resource-type link)})}
       [:template {:x-if "!editLink"}
        (if link
          [:div {:class "flex flex-row items-center gap-4 my-2"}
           (link-anchor :link link
                        :router router)
           (delete-button :media media
                          :router router)]
          [:btn {:href "#"
                 :class "btn btn-sm btn-square my-2"
                 :x-on:click "editLink=true"
                 :alt "Link"}
           (heroicons/outline-link)])]
       [:div {:x-show "editLink"} ;;:template {:x-if "editLink"}
        (media-link-form :link link
                         :media media
                         :org org
                         :router router)]]
      (html/render-partial)))

(defn handler [& {:keys [context params ::r/router request-method] :as _request}]
  ;; TODO: create an activity
  (let [{:keys [organization db resource]} context]
    (case request-method
      :post
      (let [{:keys [resource-id resource-type]} params
            result (media.i/link! db (:media/id resource) resource-id resource-type)]
        (if-not (error.i/error? result)
          (render :link result
                  :media resource
                  :org organization
                  :router router)
          ;; TODO: render an error
          (flash/error {} "Error: Could not link resource")))
      :delete
      (let [result (media.i/unlink! db (:media/id resource))]
        (tap> (str "result: " result))
        (if-not (error.i/error? result)
          (render :media resource
                  :org organization
                  :router router)
          ;; TODO: render an error
          (flash/error {} "Error: Could not unlink resource")))

      :get
      (let [link (media.i/get-link db (:media/id resource))]
        (render :link link
                :media resource
                :org organization
                :router router)))))
