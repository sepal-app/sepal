(ns sepal.test.interface
  (:require [sepal.test.core :as core]))

(defn create-system-fixture
  [config invoke keys]
  (core/create-system-fixture config invoke keys))

(defn response-anti-forgery-token [resp]
  (core/response-anti-forgery-token resp))

(defn cookie-value [session key & {:keys [host]
                                   :or {host "localhost"}}]
  (core/cookie-value session key :host host))

(defn ring-session-cookie [session & {:keys [key host]
                                      :or {key "ring-session"
                                           host "localhost"}}]
  (core/ring-session-cookie session :key key :host host))
