(ns sepal.propagation.interface.spec
  (:refer-clojure :exclude [type])
  (:require [malli.util :as mu]))

(def id pos-int?)
(def parent-accession-id pos-int?)
(def parent-material-id pos-int?)
(def rootstock-taxon-id pos-int?)
(def location-id pos-int?)
(def created-by pos-int?)
(def propagated-on :string)
(def succeeded-on :string)
(def notes :string)
(def quantity [:int {:min 0}])

(defn- name-encoder [v]
  (when v (clojure.core/name v)))

(def type
  "How the material was produced.

  Extends Bauble's three values with the methods a garden doing its own
  propagation separates. Whether a method yields the parent genotype lives on
  propagation_type.clonal in the database, not here: it is one input to the
  product the form defaults to, and the parent's material type is the other.
  Nothing validates the product against the method.

  Stored snake_case, so the keywords carry underscores: `name` is the encoder
  and the seeded keys have to match it, exactly as accession's received-type
  does."
  [:enum {:decode/store keyword
          :encode/store name-encoder}
   :seed :cutting :division :graft :layering :tissue_culture :other])

(def status
  "Where the batch is in the nursery.

  Three values, not a pipeline. `active` is the worklist and the other two
  take a batch off it."
  [:enum {:decode/store keyword
          :encode/store name-encoder}
   :active :complete :failed])

(def counts-consistent
  "You cannot have more come through than you started with.

  The message names the resolution rather than the rule, because a propagator
  hits this in May while entering a germination count against a number
  somebody wrote in March. A mass sowing belongs as null, not as an estimate;
  that convention is what makes this safe to enforce."
  [:fn {:error/message (str "More succeeded than were started. Update the "
                            "started count, or clear it if it was an estimate.")}
   (fn [{:keys [quantity-started quantity-succeeded]}]
     (or (nil? quantity-started)
         (nil? quantity-succeeded)
         (<= quantity-succeeded quantity-started)))])

(def Propagation
  [:map {:closed true}
   [:propagation/id id]
   [:propagation/type type]
   [:propagation/status status]
   [:propagation/parent-accession-id parent-accession-id]
   [:propagation/parent-material-id [:maybe parent-material-id]]
   [:propagation/rootstock-taxon-id [:maybe rootstock-taxon-id]]
   [:propagation/location-id [:maybe location-id]]
   [:propagation/propagated-on [:maybe propagated-on]]
   [:propagation/succeeded-on [:maybe succeeded-on]]
   [:propagation/quantity-started [:maybe quantity]]
   [:propagation/quantity-succeeded [:maybe quantity]]
   [:propagation/notes [:maybe notes]]
   [:propagation/created-by [:maybe created-by]]])

(def CreatePropagation
  [:and
   [:map {:closed true}
    [:type type]
    [:parent-accession-id parent-accession-id]
    [:status {:optional true} status]
    [:parent-material-id {:optional true} [:maybe parent-material-id]]
    [:rootstock-taxon-id {:optional true} [:maybe rootstock-taxon-id]]
    [:location-id {:optional true} [:maybe location-id]]
    [:propagated-on {:optional true} [:maybe propagated-on]]
    [:succeeded-on {:optional true} [:maybe succeeded-on]]
    [:quantity-started {:optional true} [:maybe quantity]]
    [:quantity-succeeded {:optional true} [:maybe quantity]]
    [:notes {:optional true} [:maybe notes]]
    [:created-by {:optional true} [:maybe created-by]]]
   counts-consistent])

(def UpdatePropagation
  [:and
   (mu/optional-keys
     [:map {:closed true}
      [:type type]
      [:status status]
      [:parent-accession-id parent-accession-id]
      [:parent-material-id [:maybe parent-material-id]]
      [:rootstock-taxon-id [:maybe rootstock-taxon-id]]
      [:location-id [:maybe location-id]]
      [:propagated-on [:maybe propagated-on]]
      [:succeeded-on [:maybe succeeded-on]]
      [:quantity-started [:maybe quantity]]
      [:quantity-succeeded [:maybe quantity]]
      [:notes [:maybe notes]]])
   counts-consistent])
