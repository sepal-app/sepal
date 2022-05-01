(ns sepal.app.http-response
  (:require [reitit.core :as r]
            [ring.util.http-response :as http]))

(defn ->path
  ([router name-or-path]
   (->path router name-or-path nil))
  ([router name-or-path args]
   (if (string? name-or-path)
     name-or-path
     (-> router
         (r/match-by-name name-or-path args)
         (r/match->path)))))

(defn found
  ([router name-or-path]
   (found router name-or-path nil))
  ([router name-or-path args]
   (http/found (->path router name-or-path args))))

(defn see-other
  ([router name-or-path]
   (see-other router name-or-path nil))
  ([router name-or-path args]
   (http/see-other (->path router name-or-path args))))
