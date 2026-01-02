(ns sepal.app.test
  (:require [peridot.core :as peri]
            [sepal.app.test.system :refer [*app*]]
            [sepal.test.interface :as test.i])
  (:import [org.jsoup Jsoup]))

(defn login
  "Login and return a peridot session"
  [email password]
  (let [{:keys [response] :as sess} (-> (peri/session *app*)
                                        (peri/request "/login"))
        token (test.i/response-anti-forgery-token response)
        sess (-> sess
                 (peri/request "/login"
                               :request-method :post
                               :params {:__anti-forgery-token token
                                        :email email
                                        :password password})
                 (peri/follow-redirect)
                 (peri/follow-redirect))]
    sess))

(defn parse-body
  "Parse response body as HTML using Jsoup."
  [response]
  (Jsoup/parse (:body response)))

(defn banner-text
  "Get the text content of the flash banner from a response.
   Returns nil if no banner is found."
  [response]
  (when-let [banner (-> (parse-body response) (.selectFirst ".banner"))]
    (.text banner)))

(defn banner-contains?
  "Check if the flash banner contains the given text.
   Returns false if no banner is found."
  [response text]
  (if-let [banner (banner-text response)]
    (.contains banner text)
    false))

(defn body-contains?
  "Check if the response body contains the given text."
  [response text]
  (let [body-text (-> (parse-body response) (.text))]
    (.contains body-text text)))
