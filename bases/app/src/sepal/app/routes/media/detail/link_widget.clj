(ns sepal.app.routes.media.detail.link-widget
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.json :as json]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.accession-combobox :as accession-combobox]
            [sepal.app.ui.combobox :as combobox]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as ui.page]
            [sepal.i18n.interface :refer [tr]]
            [zodiac.core :as z]))

(def resource-types
  [{:label (tr "Accession")
    :value "accession"}
   {:label (tr "Material")
    :value "material"}
   {:label (tr "Taxon")
    :value "taxon"}
   {:label (tr "Location")
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
  (resource-field :label (tr "Taxon")
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
  (resource-field :label (tr "Location")
                  :name name
                  :url (z/url-for location.routes/index)
                  :selected (when location-id
                              {:id location-id :text location-name})))

(defn material-field [& {:keys [material-name name material-id]}]
  (resource-field :label (tr "Material")
                  :name name
                  :url (z/url-for material.routes/index)
                  :selected (when material-id
                              {:id material-id :text material-name})))

(defn media-link-form [& {:keys [media link link-text]}]
  (form/form
    (merge ui.page/region-swap
           {:class "flex flex-col gap-2"
            :hx-post (z/url-for media.routes/detail-link {:id (:media/id media)})})
    [(form/anti-forgery-field)
     (form/field :label (tr "Resource type")
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
      (form/field :label (tr "Resource")
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
        (tr "Cancel")]
       (form/submit-button {:class "spl-btn spl-btn--sm spl-btn--primary"} (tr "Save"))]]]))

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
    [:button (merge ui.page/region-swap
                    {:type "button"
                     :class "spl-chip-icon cursor-pointer"
                     :aria-label (tr "Remove link")
                     :hx-confirm "Remove this link?"
                     :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
                     :hx-delete (z/url-for media.routes/detail-link {:id (:media/id media)})})
     (heroicons/outline-x)]]
   [:button {:type "button"
             :class "spl-btn spl-btn--sm spl-btn--icon"
             :aria-label (tr "Change link")
             :x-on:click "editLink=true"}
    (heroicons/outline-pencil-square :size 16)]])

(defn widget [& {:keys [link-info link media]}]
  [:div#media-link-root {:x-data (json/js {:editLink false
                                           :resourceType (:media-link/resource-type link)})
                         :x-on:form-saved "editLink = false"}
   ;; x-show, not x-if: a morph does not update what is inside a template, so
   ;; the chip would keep showing the link the page loaded with.
   [:div {:x-show "!editLink"}
    (if link
      (link-chip :media media
                 :text (:text link-info)
                 :url (:url link-info))
      [:div {:class "my-2"}
       [:button {:type "button"
                 :class "spl-btn spl-btn--sm spl-btn--ghost"
                 :x-on:click "editLink=true"}
        (heroicons/outline-link)
        " " (tr "Link")]])]
   [:div {:x-show "editLink"}
    (media-link-form :link link
                     :link-text (:text link-info)
                     :media media)]])
