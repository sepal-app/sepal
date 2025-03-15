(ns sepal.test.core
  (:require [integrant.core :as ig]
            [lambdaisland.uri.normalize :as uri.normalize])
  (:import [org.jsoup Jsoup]))

(defn create-system-fixture
  [config invoke keys]
  (fn [f]
    (ig/load-namespaces config)
    (let [system (ig/init config keys)]
      (try
        (invoke system f)
        (finally
          (ig/halt! system))))))

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
