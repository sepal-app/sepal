(ns sepal.app.routes.taxon.core
  (:require [reitit.core :as r]
            [sepal.app.globals :as g]
            [sepal.app.http-response :as http]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.taxon.detail :as detail]
            [sepal.app.routes.taxon.detail.media :as detail-media]
            [sepal.app.routes.taxon.detail.name :as detail-name]
            [sepal.organization.interface :as org.i]
            [sepal.taxon.interface :as taxon.i]))

(def taxon-loader (middleware/default-loader taxon.i/get-by-id :id parse-long))

;; TODO This assumes 1 org per user and is temporary until we set the
;; organization via the subdomain.
(defn require-viewer-org [handler]
  (fn [{:keys [context ::r/router viewer] :as request}]
    (let [{:keys [db]} context
          org (org.i/get-user-org db (:user/id viewer))]
      (if (some? org)
        (binding [g/*organization* org]
          (-> request
              (assoc-in [:session :organization] org)
              ;; TODO: Remove current-organization
              (assoc-in [:context :current-organization] org)
              (assoc-in [:context :organization] org)
              (handler)))
        (http/see-other router :root)))))

(defn routes []
  ["" {:middleware [[middleware/require-viewer]]}
   ["/:id" {;;:name :taxon/detail
             ;;:handler #'detail/handler
            :middleware [[middleware/resource-loader taxon-loader]
                         [middleware/require-resource-org-membership :taxon/organization-id]
                         require-viewer-org]}
    ["/" {:name :taxon/detail
          :handler #'detail/handler}]
    ["/name/" {:name :taxon/detail-name
               :handler #'detail-name/handler}]
    ["/media/" {:name :taxon/detail-media
                :handler #'detail-media/handler}]]])
