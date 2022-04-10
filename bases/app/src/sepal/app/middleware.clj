(ns sepal.app.middleware
  (:require [ring.util.http-response :refer [found]]
            [reitit.ring.middleware.exception :as exception]
            [ring.middleware.token :as token])
  (:import [com.auth0.jwt.exceptions JWTDecodeException TokenExpiredException]))

(defn wrap-context [handler global-context]
  (fn [request]
    (-> request
        (assoc :context global-context)
        handler)))

;; TODO: we're using the helpers from ring-jwt but not the middleware it
;; provides b/c it wasn't flexible enough with how it handled expired tokens.
;; Conider using the com.auth0.jwt functions directly instead of going through
;; ring-jwt
(defn wrap-jwt [handler {:keys [secret]}]
  (fn [request]
    (if-let [token (-> request :session :access-token)]
      (try
        (let [claims (token/decode token {:alg :HS256
                                          :secret secret})]
          (-> request
              (assoc :claims claims)
              handler))
        (catch JWTDecodeException e
          (println (str "Error decoding JWT token: " (ex-message e)))
          (handler request))
        (catch TokenExpiredException e
          ;; TODO: refresh token
          (println (ex-message e))
          (handler request)))
      (handler request))))

(defn require-claims-middleware
  "Redirects to /login if there are no valid claims in the request."
  [handler]
  (fn [{:keys [claims] :as request}]
    (if claims
      (handler request)
      (found "/login"))))

(defn exception-handler [message exception request]
  {:status 500
   :body {:message message
          :exception (.getClass exception)
          :data (ex-data exception)
          :uri (:uri request)}})

(def exception-middleware
  (exception/create-exception-middleware
   (merge
    exception/default-handlers
    {;; ex-data with :type ::error
     ::error (partial exception-handler "error")

       ;; ex-data with ::exception or ::failure
     ::exception (partial exception-handler "exception")

       ;; SQLException and all it's child classes
     java.sql.SQLException (partial exception-handler "sql-exception")

       ;; override the default handler
     ::exception/default (partial exception-handler "default")

       ;; print stack-traces for all exceptions
     ::exception/wrap (fn [handler e request]
                        (println "ERROR" (pr-str (:uri request)))
                        (tap> e)
                        (tap> (ex-message e))
                        (tap> (ex-data e))
                        (tap> (ex-cause e))
                        (handler e request))})))
