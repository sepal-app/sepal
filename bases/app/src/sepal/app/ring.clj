(ns sepal.app.ring
  (:require [integrant.core :as ig]
            [malli.util :as mu]
            [muuntaja.core :as m]
            [reitit.coercion.malli]
            [reitit.core :as r]
            [reitit.dev.pretty :as pretty]
            [reitit.ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.dev :as dev]
            ;; [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            ;; [reitit.ring.middleware.parameters :as parameters]
            [ring.middleware.defaults :as ring.defaults]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.stacktrace :as stacktrace]
            [sepal.app.http-response :as http-response]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.core :as accession]
            [sepal.app.routes.location.core :as location]
            [sepal.app.routes.login :as login]
            [sepal.app.routes.logout :as logout]
            [sepal.app.routes.material.core :as material]
            [sepal.app.routes.media.core :as media]
            [sepal.app.routes.org.core :as org]
            [sepal.app.routes.register.core :as register]
            [sepal.app.routes.taxon.core :as taxon]))

(defn routes []
  [""
   ["/" {:name :root
         :handler #(http-response/found (::r/router %) :org/index)
         :middleware [[middleware/require-viewer]]}]

   ["/ok" {:name :ok
           :handler (constantly {:status 204})}]

   ;; See sepal.app.html/static-url for accessing static assets
   ["/assets/*" {:name :static-files
                 :handler (reitit.ring/create-resource-handler {:root "app/dist/assets"})}]
   ["/register" (register/routes)]
   ["/login" {:name :auth/login
              :handler #'login/handler}]
   ["/logout" {:name :auth/logout
               :handler #'logout/handler}]

   ["/forgot_password" {:name :auth/forgot-pasword
                        ;; :handler #(logout/handler %)
                        }]

   ["/accession" (accession/routes)]
   ["/location" (location/routes)]
   ["/material" (material/routes)]
   ["/org" (org/routes)]
   ["/taxon" (taxon/routes)]
   ["/media" (media/routes)]])

(def error-handlers
  {:not-found (constantly {:status 404
                           :body "Not found"})})

(defn router-options [context ring-defaults]
  {:exception pretty/exception
   :data {:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
          ;;:validate spec/validate ;; enable spec validation for route data
          ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
          :coercion (reitit.coercion.malli/create
                     {;; set of keys to include in error messages
                      :error-keys #{#_:type :coercion :in :schema :value :errors :humanized #_:transformed}
                      ;; schema identity function (default: close all map schemas)
                      :compile mu/closed-schema
                      ;; strip-extra-keys (effects only predefined transformers)
                      :strip-extra-keys true
                      ;; add/set default values
                      :default-values true
                      ;; malli options
                      :options nil})
          :muuntaja m/instance
          ;; The middleware are called top to bottom
          :middleware [;; query-params & form-params, last I checked (reitit 0.7.0) this just wrapped
                       ;; ring.middleware.params/wrap-params but its not using the keywordize params
                       ;; so we'll rely on the ring middleware instead
                       ;; parameters/parameters-middleware
                       middleware/bind-globals

                       ;; content-negotiation
                       muuntaja/format-negotiate-middleware

                       ;; encoding response body
                       muuntaja/format-response-middleware

                       ;; exception handling
                       ;; exception/exception-middleware
                       ;; TODO: dev only

                       stacktrace/wrap-stacktrace-web
                       #_(exception/create-exception-middleware
                          (merge exception/default-handlers
                                 {::exception/default
                                  ;; ;; TODO: Create a pretty stacktrace formatter
                                  (fn [exc _r]
                                    {:status 500
                                     ;; TODO: Make configurable
                                     :body (with-out-str
                                             (stacktrace/print-stack-trace exc))})}))

                       ;; decoding request body
                       muuntaja/format-request-middleware
                       ;; coercing response bodys
                       coercion/coerce-response-middleware
                       ;; coercing request parameters
                       coercion/coerce-request-middleware
                       ;; multipart
                       multipart/multipart-middleware
                       [middleware/wrap-context context]
                       middleware/htmx-request
                       [ring.defaults/wrap-defaults ring-defaults]

                       ;; [middleware/wrap-hidden-method]
                       ;; [middleware/wrap-authenticated]
                       ]}})

(defmethod ig/init-key ::app [_ {:keys [ring-defaults context cookie-secret reload-per-request?]}]
  (let [ring-defaults (-> (case ring-defaults
                            :site ring.defaults/site-defaults
                            :secure-site ring.defaults/secure-site-defaults
                            ring.defaults/site-defaults)
                          ;; Don't redirect http requests to http since we
                          ;; generally run the app behind a load balancer that
                          ;; handles that for us
                          (assoc-in [:security :ssl-redirect] false)
                          ;; Use cookies for storing the session
                          (assoc-in [:session :store]
                                    (cookie-store {:key (.getBytes cookie-secret)})))
        create-handler (fn []
                         (reitit.ring/ring-handler
                          (reitit.ring/router (routes) (router-options context ring-defaults))
                          (reitit.ring/create-default-handler error-handlers)))]
    (if reload-per-request?
      (reitit.ring/reloading-ring-handler create-handler)
      (create-handler))))
