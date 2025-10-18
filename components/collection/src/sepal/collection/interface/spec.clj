(ns sepal.collection.interface.spec
  (:refer-clojure :exclude [name])
  (:require [malli.util :as mu]))

(def id pos-int?)
(def accession-id pos-int?)
(def collected-date [:time/local-date])
(def collector :string)
(def habitat :string)
;; TODO: taxon.id?
(def taxa :string)
(def remarks :string)
(def country :string)
(def province :string)
(def locality :string)

;; TODO: We're going to needs some version in and out of the database for coordinates
(def latitude :double)
(def longitude :double)
(def geo-coords [:catn [:longitude longitude] [:latitude latitude]])

(def gps-datum :string)
(def geo-uncertainty pos-int?)
(def elevation pos-int?)

(def Collection
  [:map {:closed true}
   [:collection/id id]
   [:collection/accession-id accession-id]
   [:collection/collected-date collected-date]
   [:collection/collector collector]
   [:collection/habitat habitat]
   [:collection/taxa taxa]
   [:collection/remarks remarks]
   [:collection/country country]
   [:collection/province province]
   [:collection/locality locality]
   [:collection/geo-coords geo-coords]
   [:collection/gps-datum gps-datum]
   [:collection/geo-uncertainty geo-uncertainty]
   [:collection/elevation elevation]])

(def CreateCollection
  [:map {:closed true}
   [:collected-date collected-date]
   [:collector collector]
   [:habitat habitat]
   [:taxa taxa]
   [:remarks remarks]
   [:country country]
   [:province province]
   [:locality locality]
   [:geo-coords geo-coords]
   [:gps-datum gps-datum]
   [:geo-uncertainty geo-uncertainty]
   [:elevation elevation]])

(def UpdateCollection
  (mu/optional-keys
    [:map {:closed true}
     [:collected-date collected-date]
     [:collector collector]
     [:habitat habitat]
     [:taxa taxa]
     [:remarks remarks]
     [:country country]
     [:province province]
     [:locality locality]
     [:geo-coords geo-coords]
     [:gps-datum gps-datum]
     [:geo-uncertainty geo-uncertainty]
     [:elevation elevation]]))
