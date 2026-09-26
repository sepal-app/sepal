(ns sepal.app.routes.media.detail.link
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.media.link-info :as link-info]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.accession-combobox :as accession-combobox]
            [sepal.app.ui.combobox :as combobox]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.media.interface :as media.i]
            [sepal.media.interface.activity :as media.activity]
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

(defn- resource-field
  "One of the four pickers this form swaps between.

  They share a name and an outer label, so each carries its own through
  aria-label rather than rendering a second visible one."
  [& {:keys [label name url selected]}]
  (combobox/combobox :name name
                     :label label
                     :label-hidden? true
                     :required true
                     :url url
                     :selected selected))

(defn taxon-field [& {:keys [taxon-name name taxon-id]}]
  (resource-field :label "Taxon"
                  :name name
                  :url (z/url-for taxon.routes/index)
                  :selected (when taxon-id {:id taxon-id :text taxon-name})))

(defn accession-field
  "Not a `resource-field`: accessions have their own picker. It keeps the
  same hidden label and required flag as the other three."
  [& {:keys [accession-name name accession-id]}]
  (accession-combobox/accession-combobox
    :name name
    :label-hidden? true
    :required true
    :accession-id accession-id
    :accession-text accession-name))

(defn location-field [& {:keys [location-name name location-id]}]
  (resource-field :label "Location"
                  :name name
                  :url (z/url-for location.routes/index)
                  :selected (when location-id
                              {:id location-id :text location-name})))

(defn material-field [& {:keys [material-name name material-id]}]
  (resource-field :label "Material"
                  :name name
                  :url (z/url-for material.routes/index)
                  :selected (when material-id
                              {:id material-id :text material-name})))

(defn media-link-form [& {:keys [media link link-text]}]
  (form/form
    {:class "flex flex-col gap-2"
     :hx-post (z/url-for media.routes/detail-link {:id (:media/id media)})
     :hx-target "#media-link-root"}
    [(form/anti-forgery-field)
     (form/field :label "Resource type"
                 :name "resource-type"
                 :input [:select {:name "resource-type"
                                  :class "spl-input spl-select w-full max-w-xs leading-4"
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
            :class "flex flex-col gap-2"}
      (form/field :label "Resource"
                  :name "resource-id"
                  ;; A seq, not `[:<>]`. Chassis has no fragment element, so
                  ;; that rendered a literal <<>> around these templates.
                  :input (list
                           [:template {:x-if "resourceType === 'accession'"}
                            (accession-field :name "resource-id"
                                             :accession-id (when (and link (= "accession" (:media-link/resource-type link)))
                                                             (:media-link/resource-id link))
                                             :accession-name (when (and link (= "accession" (:media-link/resource-type link)))
                                                               link-text))]
                           [:template {:x-if "resourceType === 'location'"}
                            (location-field :name "resource-id"
                                            :location-id (when (and link (= "location" (:media-link/resource-type link)))
                                                           (:media-link/resource-id link))
                                            :location-name (when (and link (= "location" (:media-link/resource-type link)))
                                                             link-text))]
                           [:template {:x-if "resourceType === 'material'"}
                            (material-field :name "resource-id"
                                            :material-id (when (and link (= "material" (:media-link/resource-type link)))
                                                           (:media-link/resource-id link))
                                            :material-name (when (and link (= "material" (:media-link/resource-type link)))
                                                             link-text))]
                           [:template {:x-if "resourceType === 'taxon'"}
                            (taxon-field :name "resource-id"
                                         :taxon-id (when (and link (= "taxon" (:media-link/resource-type link)))
                                                     (:media-link/resource-id link))
                                         :taxon-name (when (and link (= "taxon" (:media-link/resource-type link)))
                                                       link-text))]))

      ;; Cancel then Save, the order every other form in the app uses.
      [:div {:class "flex justify-end gap-2"}
       [:button {:type "button"
                 :class "spl-btn spl-btn--sm"
                 :x-on:click "editLink=false"}
        "Cancel"]
       (form/submit-button {:class "spl-btn spl-btn--sm spl-btn--primary"} "Save")]]]))

(defn link-chip
  "The link rendered as one removable chip. A chip with an x, not a tag row: a
  media item has at most one link, so the control states one thing that can be
  removed, never a list of things to add to.

  The trash icon stays reserved for deleting the media itself — the row and the
  object — which is a different act with different consequences."
  [& {:keys [media text url]}]
  [:div {:class "flex items-center gap-3 my-2"}
   [:span {:class "spl-chip"}
    (if url
      [:a {:href url :class "hover:underline"} text]
      [:span text])
    [:button {:type "button"
              :class "spl-chip-icon cursor-pointer"
              :aria-label "Remove link"
              :hx-confirm "Remove this link?"
              :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
              :hx-delete (z/url-for media.routes/detail-link {:id (:media/id media)})
              :hx-target "#media-link-root"}
     (heroicons/outline-x)]]
   [:button {:type "button"
             :class "spl-btn spl-btn--sm spl-btn--icon"
             :aria-label "Change link"
             :x-on:click "editLink=true"}
    (heroicons/outline-pencil-square :size 16)]])

(defn render [& {:keys [link-info link media]}]
  (-> [:div#media-link-root {:x-data (json/js {:editLink false
                                               :resourceType (:media-link/resource-type link)})}
       [:template {:x-if "!editLink"}
        (if link
          (link-chip :media media
                     :text (:text link-info)
                     :url (:url link-info))
          [:div {:class "my-2"}
           [:button {:type "button"
                     :class "spl-btn spl-btn--sm spl-btn--ghost"
                     :x-on:click "editLink=true"}
            (heroicons/outline-link)
            " Link"]])]
       [:div {:x-show "editLink"} ;;:template {:x-if "editLink"}
        (media-link-form :link link
                         :link-text (:text link-info)
                         :media media)]]
      (html/render-partial)))

(defn- link!
  "Link the media and record it, in one transaction. Returns the link, or the
  error from `media.i/link!`."
  [db separator media created-by resource-id resource-type]
  (db.i/with-transaction [tx db]
    (let [link (media.i/link! tx (:media/id media) resource-id resource-type)]
      (when-not (error.i/error? link)
        (media.activity/create-link! tx media.activity/linked created-by media link
                                     (:text (link-info/link-info tx link separator))))
      link)))

(defn- unlink!
  "Remove the media's link and record what it named, in one transaction."
  [db separator media created-by]
  (db.i/with-transaction [tx db]
    (when-let [link (media.i/get-link tx (:media/id media))]
      (let [text (:text (link-info/link-info tx link separator))]
        (media.i/unlink! tx (:media/id media))
        (media.activity/create-link! tx media.activity/unlinked created-by media link text)))))

(defn handler [& {:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db material-separator resource]} context]
    (case request-method
      :post
      (let [{:strs [resource-id resource-type]} form-params
            result (link! db material-separator resource (:user/id viewer)
                          resource-id resource-type)]
        (if-not (error.i/error? result)
          (render :link result
                  :link-info (link-info/link-info db result material-separator)
                  :media resource)
          ;; TODO: render an error
          (flash/error {} "Error: Could not link resource")))
      :delete
      (do (unlink! db material-separator resource (:user/id viewer))
          (render :media resource))

      :get
      (let [link (media.i/get-link db (:media/id resource))]
        (render :link-info (link-info/link-info db link material-separator)
                :link link
                :media resource)))))
