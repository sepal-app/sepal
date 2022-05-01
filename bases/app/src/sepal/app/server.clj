(ns sepal.app.server
  (:require [integrant.core :as ig]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [ring.adapter.jetty9 :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults secure-site-defaults]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [sepal.app.http-response :refer [found]]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.login :as login]
            [sepal.app.routes.logout :as logout]
            [sepal.app.routes.org.handlers :as org]
            [sepal.app.routes.register :as register]
            [sepal.app.routes.taxon.handlers :as taxon]))

(def routes
  [["/" {:name :root
         :handler #(found (::r/router %) :org-index)
         :middleware [[middleware/require-viewer-middleware]]}]
   ["/register" {:name :register
                 :handler #(register/handler %)}]
   ["/login" {:name :login
              :handler #(login/handler %)}]
   ["/logout" {:name :logout
               :handler #(logout/handler %)}]

   ["/org" {:middleware [[middleware/require-viewer-middleware]]}
    ["" {:name :org-index
         :handler org/index-handler}]
    ["/new" {:name :org-new
             :handler org/new-handler}]
    ["/create" {:name :org-create
                :handler org/create-handler}]
    ["/:org-id" {:middleware [[middleware/require-org-membership-middleware]]}
     ["/" {:name :org-detail
           :handler org/detail-handler}]
     ["/edit" {:name :org-edit}]
     ["/accession" {:name :accession-index}]

     ["/taxon"
      ["" {:name :taxon-index
           :handler taxon/index-handler}]
      ;; ["/:id" {:name :taxon-index
      ;;      :handler taxon/index-handler}]
      ]
     ["/location" {:name :location-index}]
     ["/media" {:name :media-index}]]]])

(defn default-router-options [{:keys [global-context cookie-secret]}]
  {:data {:middleware [[middleware/exception-middleware]
                       ;; TODO: Use secure-site-defaults in production
                       [wrap-defaults (-> site-defaults
                                          ;; TODO: Set a key for the cookie secret, set docs:
                                          ;; https://ring-clojure.github.io/ring/ring.middleware.session.cookie.html
                                          ;; (assoc-in [:session :store] (cookie-store {:key cookie-secret}))
                                          (assoc-in [:session :store]
                                                    (cookie-store {:key (.getBytes cookie-secret)})))]
                       [middleware/wrap-context global-context]]}})

(defmethod ig/init-key ::server [_ cfg]
  (-> routes
      (ring/router (default-router-options cfg))
      (ring/ring-handler (ring/create-default-handler) ; {:inject-match? false}
                         )
      (jetty/run-jetty {:port (:port cfg)
                        :join? false})))

(defmethod ig/halt-key! ::server [_ server]
  (jetty/stop-server server))
