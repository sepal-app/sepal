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
            [sepal.app.ui.accession-combobox :as accession-combobox]
            [sepal.app.ui.combobox :as combobox]
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
    {:class "flex flex-row gap-2 items-center"
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
            :class "flex flex-row gap-2 items-end flex-grow"}
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
      [:button {:type "button"
                :class "spl-btn spl-btn--sm mb-4"
                :x-on:click "editLink=false"}
       "Cancel"]
      (form/submit-button {:class "spl-btn spl-btn--sm spl-btn--primary mb-4"} "Save")]]))

(defmulti link-text
  (fn [_db link]
    (:media-link/resource-type link)))

;; A `media_link` row carrying a resource type nothing here recognises should
;; degrade to showing what is stored, not throw. Nothing the UI can write today
;; produces one — `resource-types` is a fixed list — but the database is not.
(defmethod link-text :default
  [_db link]
  (:media-link/resource-type link))

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

(defn link-info
  "The display text and destination for a link, as data rather than markup. An
  unrecognised resource type yields no URL — the chip renders as text."
  [& {:keys [db link]}]
  (let [url (case (:media-link/resource-type link)
              "accession" (z/url-for accession.routes/detail {:id (:media-link/resource-id link)})
              "location" (z/url-for location.routes/detail {:id (:media-link/resource-id link)})
              "material" (z/url-for material.routes/detail {:id (:media-link/resource-id link)})
              "taxon" (z/url-for taxon.routes/detail {:id (:media-link/resource-id link)})
              nil)]
    {:text (link-text db link)
     :url url}))

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

(defn handler [& {:keys [::z/context params request-method] :as _request}]
  ;; TODO: create an activity
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (let [{:keys [resource-id resource-type]} params
            result (media.i/link! db (:media/id resource) resource-id resource-type)]
        (if-not (error.i/error? result)
          (render :link result
                  :link-info (link-info :db db :link result)
                  :media resource)
          ;; TODO: render an error
          (flash/error {} "Error: Could not link resource")))
      :delete
      (let [result (media.i/unlink! db (:media/id resource))]
        (if-not (error.i/error? result)
          (render :media resource)
          ;; TODO: render an error
          (flash/error {} "Error: Could not unlink resource")))

      :get
      ;; The widget renders for media with or without a link. link-info runs a
      ;; query per link type, so it is only computed when there is one.
      (let [link (media.i/get-link db (:media/id resource))
            link-info (when link
                        (link-info :db db :link link))]
        (render :link-info link-info
                :link link
                :media resource)))))
