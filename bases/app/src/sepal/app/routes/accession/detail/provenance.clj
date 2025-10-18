(ns sepal.app.routes.accession.detail.provenance
  (:require [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.app.http-response :as http]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.detail.tabs :as accession.tabs]
            [sepal.app.routes.accession.form :as accession.form]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.tabs :as tabs]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

;; TODO: Rename this provenance

;; TODO:
;; source can be collection, donation, purchase, propagation
;;
;; From bauble: https://github.com/Bauble/bauble.classic/blob/312303f84643f4bc14ab00464c63ed9857c8819d/bauble/plugins/garden/source.py
;;
;; create table source (
;;   sources_code text,
;;   TODO: Seems like accession.source_id would be better so we can't have multiple
;;   accession_id int,
;;   source_detail_id,
;;   collection,
;;   # relation to a propagation that is specific to this Source and
;;   # not attached to a Plant
;;   propagation_id,
;;   # relation to a Propagation that already exists and is attached
;;   # to a Plant
;;   material_propagation_id
;; )
;;
;; source_type_values = [(u'Expedition', _('Expedition')),
                      ;; (u'GeneBank', _('Gene Bank')),
                      ;; (u'BG', _('Botanic Garden or Arboretum')),
                      ;; (u'Research/FieldStation', _('Research/Field Station')),
                      ;; (u'Staff', _('Staff member')),
                      ;; (u'UniversityDepartment', _('University Department')),
                      ;; (u'Club', _('Horticultural Association/Garden Club')),
                      ;; (u'MunicipalDepartment', _('Municipal department')),
                      ;; (u'Commercial', _('Nursery/Commercial')),
                      ;; (u'Individual', _('Individual')),
                      ;; (u'Other', _('Other')),
                      ;; (u'Unknown', _('Unknown')),
                      ;; (None, '')]
;; From Hortis
;; Provenance: Garden (G), Wild (W), Garden - wild origin (Z), U
;; Material Received: Plant, Seed, Cutting, Seedling, Bulb or corm, Rhizome
;; Donor/supplier: Select from a list
;; IPEN number
;;

(defn render [& {:keys []}])

(defn save! [db id user data])

(def FormParams
  [:map {:closed true}
   [:code :string]
   [:taxon-id :int]])

(defn handler [{:keys [::z/context form-params request-method viewer] :as request}]
  (let [{:keys [db organization resource]} context
        taxon (accession.i/get-by-id db (:accession/taxon-id resource))
        values (merge {:id (:accession/id resource)
                       :code (:accession/code resource)
                       :taxon-id (:accession/taxon-id resource)
                       :taxon-name (:taxon/name taxon)}
                      (params/decode FormParams form-params))]

    (case request-method
      :post
      (let [result (save! db (:accession/id resource) (:user/id viewer) values)]
        ;; TODO: handle errors
        (if-not (error.i/error? result)
          (http/found accession.routes/detail {:id (:accession/id resource)})
          (-> (http/found accession.routes/detail {:id (:accession/id resource)})
              ;; TODO: The errors needs to be parsed here and return a message
              (assoc :flash {:error result
                             :values form-params}))))

      (render :org organization
              :accession resource
              :taxon taxon
              :values values))))
