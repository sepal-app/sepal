(ns sepal.app.routes.propagation.product
  "Creating the material or accession a propagation produced.

  Nothing here is a constraint: the database permits every combination. The
  three-branch rule below only decides which product the form offers first,
  and the other kind is always available as a secondary action."
  (:require [failjure.core :as f]
            [sepal.accession.interface :as accession.i]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.app.codes :as codes]
            [sepal.app.datetime :as datetime]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.database.interface :as db.i]
            [sepal.material.interface :as material.i]
            [sepal.material.interface.activity :as material.activity]
            [sepal.propagation.interface :as propagation.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(defn default-kind
  "Which product a propagation produced, in order.

  1. The parent material is itself the accessioned propagule -- type `seed`,
     `vegetative` or `tissue` -- so growing it on yields new material under
     the same accession, whatever the method. This is the case a method-only
     rule gets wrong, and it is the commonest propagation a garden does: an
     accession arrives as a seed packet, and the plants raised from it are
     that accession.

  2. Otherwise the method is clonal, so new material under the same
     accession.

  3. Otherwise -- seed off a growing plant -- a new accession, because the
     seed is a genotype the parent accession does not cover."
  [parent-material clonal?]
  (cond
    (contains? #{:seed :vegetative :tissue} (:material/type parent-material))
    :material

    clonal?
    :material

    :else
    :accession))

(defn- clonal?
  "The method's own answer to whether it yields the parent genotype. SQLite
  hands the flag back as a boolean or a 0/1 integer depending on the column,
  so both are accepted."
  [db propagation]
  (let [truthy? (fn [v] (not (or (nil? v) (false? v) (and (number? v) (zero? v)))))]
    (some (fn [type]
            (when (= (:propagation-type/name type) (name (:propagation/type propagation)))
              (truthy? (:propagation-type/clonal type))))
          (propagation.i/list-types db))))

(defn default-kind-for
  "The default product for this propagation, read from the database."
  [db propagation]
  (default-kind (some->> (:propagation/parent-material-id propagation)
                         (material.i/get-by-id db))
                (clonal? db propagation)))

(defn- create-material!
  "New material under the parent accession. The location falls back to the
  accession's intended location when the batch was not given one."
  [tx propagation parent created-by today]
  (let [accession-id (:propagation/parent-accession-id propagation)
        material (material.i/create!
                   tx {:code (material.i/next-code tx
                                                   (:template (codes/material tx))
                                                   accession-id
                                                   today)
                       :accession-id accession-id
                       :location-id (or (:propagation/location-id propagation)
                                        (:accession/intended-location-id parent))
                       :type :plant
                       :status :alive
                       :quantity 1
                       :propagation-id (:propagation/id propagation)})]
    (material.activity/create! tx material.activity/created created-by material)
    material))

(defn- create-accession!
  "A new accession for a new genotype. The taxon comes from the parent; the
  receipt fields are left out, because an accession raised from a propagation
  did not arrive."
  [tx propagation parent created-by today]
  (let [accession (accession.i/create!
                    tx {:code (accession.i/next-code tx
                                                     (:template (codes/accession tx))
                                                     today)
                        :taxon-id (:accession/taxon-id parent)
                        :propagation-id (:propagation/id propagation)})]
    (accession.activity/create! tx accession.activity/created created-by accession)
    accession))

(def FormParams
  [:map {:closed true}
   [:kind {:optional true} [:enum :material :accession]]])

(defn handler
  "Create the product and set its link in one transaction. The kind defaults
  when the form does not name one, which is how the primary action works."
  [{:keys [::z/context form-params request-method viewer]}]
  (let [{:keys [db resource timezone]} context
        parent (accession.i/get-by-id db (:propagation/parent-accession-id resource))
        default (default-kind-for db resource)]
    (if (= :get request-method)
      ;; The action lives on the record page; this route only writes.
      (http/found propagation.routes/detail {:id (:propagation/id resource)})
      (let [detail-redirect (http/hx-redirect propagation.routes/detail
                                              {:id (:propagation/id resource)})]
        (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)
                        kind (or (:kind data) default)
                        today (datetime/today timezone)]
          (if (and (= kind :material)
                   (nil? (or (:propagation/location-id resource)
                             (:accession/intended-location-id parent))))
            (flash/error detail-redirect
                         (str "Material needs a location. Set one on this batch "
                              "or an intended location on the accession."))
            (f/attempt-all [product (f/try*
                                      (db.i/with-transaction [tx db]
                                        (case kind
                                          :material (create-material! tx resource parent (:user/id viewer) today)
                                          :accession (create-accession! tx resource parent (:user/id viewer) today))))]
              (case kind
                :material (-> (http/hx-redirect material.routes/detail
                                                {:id (:material/id product)})
                              (flash/success "Material created"))
                :accession (-> (http/hx-redirect accession.routes/detail
                                                 {:id (:accession/id product)})
                               (flash/success "Accession created")))
              (f/when-failed [e]
                (http/failure-flash e detail-redirect "Could not create the product"))))
          (f/when-failed [e]
            (http/failure-flash e detail-redirect "Could not create the product")))))))
