(ns sepal.app.core
  (:require [integrant.core :as ig]
            [liana.core :as liana]
            [reitit.core :as r]
            [reitit.ring :as r.ring]
            [sepal.app.http-response :as http]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.login :as login]
            [sepal.app.routes.logout :as logout]
            ;; [sepal.app.routes.org.handlers :as org]
            [sepal.app.routes.register :as register]))

(defmethod ig/init-key ::routes [_ _]
  [["/" {:name :root
         :handler #(http/found (::r/router %) :org-index)
         :middleware [[middleware/require-viewer-middleware]]}]
   ["/register" {:name :register
                 :handler #(register/handler %)}]
   ["/login" {:name :login
              :handler #(login/handler %)}]
   ;; ["/logout" {:name :logout
   ;;             :handler #(logout/handler %)}]
   ;; ["/assets/{*path}" {:name :assets
   ;;                     :handler (r.ring/create-resource-handler {:parameter :path})}]

   ["/assets/*" {:name :assets
                 :handler (r.ring/create-resource-handler)}]

   ;; ["/org" {:middleware [[middleware/require-viewer-middleware]]}
   ;;  ["" {:name :org-index
   ;;       :handler org/index-handler}]
   ;;  ["/new" {:name :org-new
   ;;           :handler org/new-handler}]
   ;;  ["/create" {:name :org-create
   ;;              :handler org/create-handler}]
   ;;  ["/:org-id" {:middleware [[middleware/require-org-membership-middleware]]}
   ;;   ["/" {:name :org-detail
   ;;         :handler org/detail-handler}]
   ;;   ["/edit" {:name :org-edit}]
   ;;   ["/accession" {:name :accession-index}]

   ;;   ;; (resources/make-resource-routes "taxon"
   ;;   ;;                                 'sepal.app.routes.taxon.controller)
   ;;   ["/location" {:name :location-index}]
   ;;   ["/media" {:name :media-index}]]]
   ])

(defmethod ig/init-key ::liana [_ opts]
  (liana/start opts))

(defmethod ig/halt-key! ::liana [_ instance]
  (liana/stop instance))

(defmethod ig/init-key ::router [_ {:keys [liana]}]
  (:liana.core/router liana))
