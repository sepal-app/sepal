(ns sepal.postmark.core
  (:refer-clojure :exclude [send])
  (:require [clojure.data.json :as json]
            [hato.client :as hc]
            [sepal.postmark.interface.protocols :as postmark.p]))

(deftype PostmarkService [api-key]
  postmark.p/PostmarkService

  (email [_ data]
    (hc/post "https://api.postmarkapp.com/email"
             {:content-type :json
              :accept :json
              :throw-exceptions? false
              :headers {"X-Postmark-Server-Token" api-key}
              :body (json/write-str data)})))
