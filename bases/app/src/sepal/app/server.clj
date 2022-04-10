(ns sepal.app.server
  (:require [integrant.core :as ig]
            [reitit.ring :as ring]
            [ring.adapter.jetty9 :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults secure-site-defaults]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [sepal.app.routes.login :as login]
            [sepal.app.routes.logout :as logout]
            [sepal.app.middleware :as middleware]))

(def routes
  [["/" {:handler (fn [_]
                    {:status 200
                     :headers {"content-type" "text/plain"}
                     :body "hi"})
         :middleware [[middleware/require-claims-middleware]]}]
   ["/login" {:handler #(login/handler %)}]
   ["/logout" {:handler #(logout/handler %)}]])

(defn default-router-options [{:keys [global-context cookie-secret jwt-secret]}]
  {:data {:middleware [[middleware/exception-middleware]
                       ;; TODO: Use secure-site-defaults in production
                       [wrap-defaults (-> site-defaults
                                          ;; TODO: Set a key for the cookie secret, set docs:
                                          ;; https://ring-clojure.github.io/ring/ring.middleware.session.cookie.html
                                          ;; (assoc-in [:session :store] (cookie-store {:key cookie-secret}))
                                          (assoc-in [:session :store]
                                                    (cookie-store {:key (.getBytes cookie-secret)})))]
                       [middleware/wrap-jwt {:secret jwt-secret}]
                       [middleware/wrap-context global-context]]}})

(defmethod ig/init-key ::server [_ cfg]
  (-> routes
      (ring/router (default-router-options cfg))
      (ring/ring-handler (ring/create-default-handler) {:inject-match? false})
      (jetty/run-jetty {:port (:port cfg)
                        :join? false})))

(defmethod ig/halt-key! ::server [_ server]
  (jetty/stop-server server))
