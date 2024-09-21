(ns sepal.postmark.interface
  (:require [integrant.core :as ig]
            [sepal.postmark.core :as core]
            [sepal.postmark.interface.protocols :as postmark.p]))

(defmethod ig/init-key ::service [_ {:keys [api-key]}]
  (core/->PostmarkService api-key))

(defn email
  "Interact with the Postmark /email API"
  [service data]
  (postmark.p/email service data))
