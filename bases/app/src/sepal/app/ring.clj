(ns sepal.app.ring
  (:require [integrant.core :as ig]
            [muuntaja.core :as m]
            [reitit.coercion.malli]
            [reitit.core :as r]
            [reitit.dev.pretty :as pretty]
            [reitit.ring]
            [reitit.ring.middleware.dev :as dev]
            [sepal.app.http-response :as http-response]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.accession.core :as accession]
            [sepal.app.routes.auth.core :as auth]
            [sepal.app.routes.location.core :as location]
            [sepal.app.routes.material.core :as material]
            [sepal.app.routes.media.core :as media]
            [sepal.app.routes.org.core :as org]
            [sepal.app.routes.org.routes :as org.routes]
            [sepal.app.routes.taxon.core :as taxon]))

(defn routes []
  (concat
    (auth/routes)
    [""
     ["/" {:name :root
           :handler #(http-response/found (::r/router %) org.routes/index)
           :middleware [[middleware/require-viewer]]}]
     ["/ok" {:name :ok
             :handler (constantly {:status 204})}]
     ;; See sepal.app.html/static-url for accessing static assets
     ["/assets/*" {:name :static-files
                   :handler (reitit.ring/create-resource-handler {:root "app/dist/assets"})}]
     ["/accession" (accession/routes)]
     ["/location" (location/routes)]
     ["/material" (material/routes)]
     ["/org" (org/routes)]
     ["/taxon" (taxon/routes)]
     ["/media" (media/routes)]]))

;; TODO: We need to expand on the error handlers
(def error-handlers
  {:not-found (constantly {:status 404
                           :body "Not found"})})

(defmethod ig/init-key ::app [_ {:keys [middleware reload-per-request? print-request-diffs?]}]
  (let [router-options (cond->  {:exception pretty/exception
                                 :data {:muuntaja m/instance
                                        :middleware middleware}}
                         ;; Print out a diff of the request between each
                         ;; middleware. Should only be run in dev mode.
                         print-request-diffs?
                         (assoc :reitit.middleware/transform dev/print-request-diffs))
        create-handler (fn []
                         (reitit.ring/ring-handler
                           (reitit.ring/router (routes) router-options)
                           (reitit.ring/create-default-handler error-handlers)))]
    (if reload-per-request?
      (reitit.ring/reloading-ring-handler create-handler)
      (create-handler))))
