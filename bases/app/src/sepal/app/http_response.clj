(ns sepal.app.http-response
  (:require [ring.util.http-response :as http]
            [sepal.app.router :as router]))

;; TODO: move to router ns?

(defn found
  ([router name-or-path]
   (found router name-or-path nil))
  ([router name-or-path args]
   (http/found (router/url-for router name-or-path args))))

(defn see-other
  ([router name-or-path]
   (see-other router name-or-path nil))
  ([router name-or-path args]
   (http/see-other (router/url-for router name-or-path args))))

(defn not-found []
  (http/not-found))
