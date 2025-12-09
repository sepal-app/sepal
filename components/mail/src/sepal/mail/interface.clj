(ns sepal.mail.interface
  (:require [integrant.core :as ig]
            [sepal.mail.core :as core]
            [sepal.mail.interface.protocols :as mail.p]))

(defmethod ig/init-key ::client [_ opts]
  (core/->SmtpClient (core/create-session opts)))

(defn send-message
  "Send an email message. Message is a map with keys:
   :from         - sender email address
   :to           - recipient email address (string or collection of strings)
   :subject      - email subject
   :body         - email body content
   :content-type - optional, defaults to \"text/plain\""
  [client message]
  (mail.p/send-message client message))
