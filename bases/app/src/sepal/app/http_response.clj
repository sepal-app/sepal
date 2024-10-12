(ns sepal.app.http-response
  (:require [ring.util.http-response :as http]
            [zodiac.core :as z]))

(defn found
  ([name-or-path]
   (found name-or-path nil))
  ([name-or-path args]
   (http/found (z/url-for name-or-path args))))

(defn see-other
  ([name-or-path]
   (see-other name-or-path nil))
  ([name-or-path args]
   (http/see-other (z/url-for name-or-path args))))

(defn not-found []
  (http/not-found))
