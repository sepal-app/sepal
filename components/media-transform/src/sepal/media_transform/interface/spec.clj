(ns sepal.media-transform.interface.spec
  "Malli specs for image transformation parameters.")

(def fit
  [:enum :crop :contain])

(def output-format
  [:enum :jpg :png :original])

(def quality
  [:int {:min 1 :max 100}])

(def TransformParams
  "Parameters for image transformation."
  [:map
   [:width {:optional true} pos-int?]
   [:height {:optional true} pos-int?]
   [:fit {:optional true} fit]
   [:quality {:optional true} quality]
   [:format {:optional true} output-format]])

(def CacheEntry
  "Schema for a cache entry in the database."
  [:map
   [:hash :string]
   [:media-id pos-int?]
   [:size-bytes pos-int?]
   [:accessed-at inst?]])
