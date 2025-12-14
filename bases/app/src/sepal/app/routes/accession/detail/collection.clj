(ns sepal.app.routes.accession.detail.collection
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.accession.detail.shared :as accession.shared]
            [sepal.app.routes.accession.panel :as accession.panel]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.pages.detail :as pages.detail]
            [sepal.collection.interface :as coll.i]
            [sepal.collection.interface.datum :as datum]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn form [& {:keys [action errors values]}]
  [:div
   (ui.form/form
     {:id "collection-form"
      :hx-post action
      :hx-swap "none"
      :x-on:collection-form:submit.window "$el.requestSubmit()"
      :x-on:collection-form:reset.window "$el.reset()"}
     [:div {:class "max-w-3xl"}
      (ui.form/anti-forgery-field)

      ;; Collection metadata section
      [:div {:class "grid grid-cols-2 gap-8"}
       [:div
        (ui.form/input-field :label "Collector"
                             :name "collector"
                             :value (:collector values)
                             :errors (:collector errors))
        (ui.form/input-field :label "Collection Date"
                             :name "collected-date"
                             :type "date"
                             :value (:collected-date values)
                             :errors (:collected-date errors))]
       [:div
        (ui.form/textarea-field :label "Habitat"
                                :name "habitat"
                                :id "habitat"
                                :value (:habitat values)
                                :errors (:habitat errors))]]

      ;; Taxa and remarks
      [:div {:class "grid grid-cols-2 gap-8 mt-4"}
       [:div
        (ui.form/textarea-field :label "Associated Taxa"
                                :name "taxa"
                                :id "taxa"
                                :value (:taxa values)
                                :errors (:taxa errors))]
       [:div
        (ui.form/textarea-field :label "Remarks"
                                :name "remarks"
                                :id "remarks"
                                :value (:remarks values)
                                :errors (:remarks errors))]]

      ;; Location section
      [:h3 {:class "text-lg font-semibold mt-6 mb-4"} "Location"]
      [:div {:class "grid grid-cols-3 gap-4"}
       (ui.form/input-field :label "Country"
                            :name "country"
                            :value (:country values)
                            :errors (:country errors))
       (ui.form/input-field :label "Province/State"
                            :name "province"
                            :value (:province values)
                            :errors (:province errors))
       (ui.form/input-field :label "Locality"
                            :name "locality"
                            :value (:locality values)
                            :errors (:locality errors))]

      ;; Geo coordinates section
      [:h3 {:class "text-lg font-semibold mt-6 mb-4"} "Coordinates"]
      [:div {:class "grid grid-cols-5 gap-4"}
       (ui.form/input-field :label "Latitude"
                            :name "lat"
                            :type "number"
                            :value (:lat values)
                            :errors (:lat errors)
                            :input-attrs {:step "any"
                                          :min "-90"
                                          :max "90"})
       (ui.form/input-field :label "Longitude"
                            :name "lng"
                            :type "number"
                            :value (:lng values)
                            :errors (:lng errors)
                            :input-attrs {:step "any"
                                          :min "-180"
                                          :max "180"})
       (let [current-srid (or (:srid values) datum/default-srid)]
         (ui.form/field :label "Coordinate System"
                        :name "srid"
                        :errors (:srid errors)
                        :input [:select {:name "srid"
                                         :id "srid"
                                         :class "select select-bordered select-md w-full"}
                                (for [[srid label] datum/datum-options]
                                  [:option {:value srid
                                            :selected (when (= srid current-srid) "selected")}
                                   label])]))
       (ui.form/input-field :label "Uncertainty (m)"
                            :name "geo-uncertainty"
                            :type "number"
                            :value (:geo-uncertainty values)
                            :errors (:geo-uncertainty errors)
                            :input-attrs {:min "1"})
       (ui.form/input-field :label "Elevation (m)"
                            :name "elevation"
                            :type "number"
                            :value (:elevation values)
                            :errors (:elevation errors))]])])

(defn page-content [& {:keys [errors accession values]}]
  [:div {:class "flex flex-col gap-2"}
   (accession.shared/tabs accession accession.shared/collection-tab)
   (form :action (z/url-for accession.routes/detail-collection {:id (:accession/id accession)})
         :errors errors
         :values values)])

(defn footer-buttons []
  [[:button {:class "btn"
             :x-on:click "confirm('Are you sure you want to lose your changes?') && location.reload()"}
    "Cancel"]
   [:button {:class "btn btn-primary"
             :x-on:click "$dispatch('collection-form:submit')"}
    "Save"]])

(defn render [& {:keys [errors accession taxon values panel-data]}]
  (page/page :content (pages.detail/page-content-with-panel
                        :content (page-content :errors errors
                                               :accession accession
                                               :values values)
                        :panel-content (accession.panel/panel-content
                                         :accession (:accession panel-data)
                                         :taxon (:taxon panel-data)
                                         :supplier (:supplier panel-data)
                                         :stats (:stats panel-data)
                                         :activities (:activities panel-data)
                                         :activity-count (:activity-count panel-data)))
             :breadcrumbs (accession.shared/breadcrumbs taxon accession)
             :footer (ui.form/footer :buttons (footer-buttons))))

(defn collection->values [collection]
  (when collection
    (let [geo (:collection/geo-coordinates collection)]
      {:id (:collection/id collection)
       :collected-date (:collection/collected-date collection)
       :collector (:collection/collector collection)
       :habitat (:collection/habitat collection)
       :taxa (:collection/taxa collection)
       :remarks (:collection/remarks collection)
       :country (:collection/country collection)
       :province (:collection/province collection)
       :locality (:collection/locality collection)
       :lat (:lat geo)
       :lng (:lng geo)
       :srid (:srid geo)
       :geo-uncertainty (:collection/geo-uncertainty collection)
       :elevation (:collection/elevation collection)})))

(defn save! [db accession-id data]
  (let [existing (coll.i/get-by-accession-id db accession-id)]
    (if existing
      ;; Update existing collection
      (coll.i/update! db (:collection/id existing) data)
      ;; No existing collection, create one
      (coll.i/create! db (assoc data :accession-id accession-id)))))

(def FormParams
  [:map {:closed true}
   [:collected-date {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:collector {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:habitat {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:taxa {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:remarks {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:country {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:province {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:locality {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:lat {:decode/form validation.i/empty->nil} [:maybe [:double {:min -90 :max 90}]]]
   [:lng {:decode/form validation.i/empty->nil} [:maybe [:double {:min -180 :max 180}]]]
   [:srid :int]
   [:geo-uncertainty {:decode/form validation.i/empty->nil} [:maybe [:int {:min 1}]]]
   [:elevation {:decode/form validation.i/empty->nil} [:maybe :int]]])

(defn form-params->collection-data
  "Convert validated form params to collection data structure.
   Handles geo-coordinates specially - only includes if both lat and lng are present."
  [{:keys [lat lng srid] :as params}]
  (let [base-data (dissoc params :lat :lng :srid)]
    (if (and lat lng)
      (assoc base-data :geo-coordinates {:lat lat :lng lng :srid srid})
      base-data)))

(defn handler [{:keys [::z/context form-params request-method]}]
  (let [{:keys [db resource]} context
        accession resource
        taxon (taxon.i/get-by-id db (:accession/taxon-id accession))
        collection (coll.i/get-by-accession-id db (:accession/id accession))
        values (if collection
                 (collection->values collection)
                 {})]

    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [coll-data (form-params->collection-data result)
                saved (save! db (:accession/id accession) coll-data)]
            (if-not (error.i/error? saved)
              (http/hx-redirect (z/url-for accession.routes/detail-collection
                                           {:id (:accession/id accession)}))
              (http/validation-errors (validation.i/humanize saved))))))

      (let [panel-data (accession.panel/fetch-panel-data db accession)]
        (render :accession accession
                :taxon taxon
                :values values
                :panel-data panel-data)))))
