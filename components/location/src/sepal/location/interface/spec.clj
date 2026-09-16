(ns sepal.location.interface.spec
  (:refer-clojure :exclude [name])
  (:require [malli.util :as mu]))

(def id pos-int?)
(def name [:string {:min 2}])
(def code [:string {:min 2}])
(def description :string)

(defn- name-encoder [v]
  (when v (clojure.core/name v)))

(def status
  "Whether the garden still files material here.

  An archived location keeps its row and its name, because material_change
  names it as the source or destination of moves that already happened and a
  history that reads \"from nowhere\" is worse than a list with a retired bed
  in it. It leaves the pickers instead, so nothing new arrives."
  [:enum {:decode/store keyword
          :encode/store name-encoder}
   :active :archived])

(def Location
  [:map {:closed true}
   [:location/id id]
   [:location/code code]
   [:location/description [:maybe description]]
   [:location/name name]
   [:location/status status]])

(def CreateLocation
  [:map {:closed true}
   [:code code]
   [:name name]
   [:description {:optional true} [:maybe description]]])

(def UpdateLocation
  (mu/optional-keys
    [:map {:closed true}
     [:code code]
     [:name name]
     [:description [:maybe description]]
     [:status status]]))
