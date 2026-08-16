(ns sepal.test.core
  (:require [lambdaisland.uri.normalize :as uri.normalize])
  (:import [org.jsoup Jsoup]))

(defn response-anti-forgery-token [resp]
  (-> resp
      :body
      Jsoup/parse
      (.selectFirst "input[name=__anti-forgery-token]")
      (.attr "value")))

(defn cookie-value [session key & {:keys [host]}]
  (-> session
      (get-in [:cookie-jar host  key :value])
      (uri.normalize/percent-decode)))

(defn ring-session-cookie [session & {:keys [key host]}]
  (cookie-value session key :host host))
