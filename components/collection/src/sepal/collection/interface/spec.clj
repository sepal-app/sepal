(ns sepal.collection.interface.spec
  (:require [camel-snake-kebab.core :as csk]
            [clojure.data.json :as json]
            [malli.util :as mu]
            [sepal.validation.interface :as validate.i]))

(def id pos-int?)
(def accession-id pos-int?)
(def collected-date [:maybe :string])
(def collector [:maybe :string])
(def habitat [:maybe :string])
(def taxa [:maybe :string])
(def remarks [:maybe :string])
(def country [:maybe :string])
(def province [:maybe :string])
(def locality [:maybe :string])
(def geo-uncertainty [:maybe pos-int?])
(def elevation [:maybe :int])

(def default-srid 4326)

(def GeoPoint
  [:map
   [:lat number?]
   [:lng number?]
   [:srid {:optional true} pos-int?]])

(defn- parse-geo-json
  "Parse geo-coordinates JSON string to a map with keyword keys.
   Returns nil if the JSON contains only null values (i.e., when geo_coordinates column is NULL)."
  [s]
  (when s
    (let [parsed (-> (json/read-str s)
                     (update-keys csk/->kebab-case-keyword))]
      ;; When geo_coordinates is NULL, json_object returns {"lat":null,"lng":null,"srid":null}
      ;; We treat this as nil to match the expected [:maybe GeoPoint] schema
      (when (some? (:lat parsed))
        parsed))))

(def Collection
  [:map
   [:collection/id id]
   [:collection/collected-date collected-date]
   [:collection/collector collector]
   [:collection/habitat habitat]
   [:collection/taxa taxa]
   [:collection/remarks remarks]
   [:collection/country country]
   [:collection/province province]
   [:collection/locality locality]
   [:collection/geo-coordinates {:optional true
                                 :decode/store parse-geo-json}
    [:maybe GeoPoint]]
   [:collection/geo-uncertainty geo-uncertainty]
   [:collection/elevation elevation]
   [:collection/accession-id accession-id]])

(def CreateCollection
  [:map {:closed true}
   [:accession-id {:decode/store validate.i/coerce-int} accession-id]
   [:collected-date {:optional true} collected-date]
   [:collector {:optional true} collector]
   [:habitat {:optional true} habitat]
   [:taxa {:optional true} taxa]
   [:remarks {:optional true} remarks]
   [:country {:optional true} country]
   [:province {:optional true} province]
   [:locality {:optional true} locality]
   [:geo-coordinates {:optional true} [:maybe GeoPoint]]
   [:geo-uncertainty {:optional true} geo-uncertainty]
   [:elevation {:optional true} elevation]])

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
     [:geo-coordinates [:maybe GeoPoint]]
     [:geo-uncertainty geo-uncertainty]
     [:elevation elevation]]))
