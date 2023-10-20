(ns sepal.app.core
  (:require [integrant.core :as ig]
            ;; [liana.core :as liana]
            [reitit.core :as r]
            [reitit.ring :as r.ring]
            [sepal.app.http-response :as http]
            [sepal.app.middleware :as middleware]
            [sepal.app.routes.dashboard :as dashboard]
            [sepal.app.routes.login :as login]
            [sepal.app.routes.logout :as logout]
            ;; [sepal.app.routes.org.handlers :as org]
            [sepal.app.routes.register.index :as register]
            [sepal.app.routes.taxon.handlers :as taxon]))


;; TODO: This can all be removed

;; #_(defmethod ig/init-key ::routes [_ _]
;;   [["/" {:name :root
;;          :handler #(http/found (::r/router %) :org/index)
;;          :middleware [[middleware/require-viewer]]}]

;;    ["/register" {:name :auth/register
;;                  :handler #(register/handler %)}]
;;    ["/login" {:name :auth/login
;;               :handler #(login/handler %)}]
;;    ["/logout" {:name :auth/logout
;;                :handler #(logout/handler %)}]

;;    ["/dashboard" {:name :dashboard
;;                   :handler #(dashboard/handler %)}]

;;    ["/forgot_password" {:name :auth/forgot-pasword
;;                         ;; :handler #(logout/handler %)
;;                         }]

;;    ;; ["/profile" {:name :profile
;;    ;;             :handler #(logout/handler %)}]
;;    ;; ["/assets/{*path}" {:name :assets
;;    ;;                     :handler (r.ring/create-resource-handler {:parameter :path})}]

;;    ["/assets/*" {:name :assets
;;                  :handler (r.ring/create-resource-handler)}]

;;    ["/org" {:middleware [[middleware/require-viewer]]}
;;     ["" {:name :org/index
;;          :handler org/index-handler}]
;;     ["/create" {:name :org/create
;;                 :handler org/create-handler}]
;;     ["/:org_id" {:middleware [[middleware/require-org-membership-middleware]]}
;;      ["/" {:name :org/detail
;;            :handler org/detail-handler}]
;;      ["/edit" {:name :org/edit}]
;;      ;; ["/accession" {:name :accession/old}]

;;      ;; (resources/make-resource-routes "taxon"
;;      ;;                                 'sepal.app.routes.taxon.controller)
;;      ;; ["/location" {:name :location/index}]
;;      ;; ["/media" {:name :media/index}]
;;      ]]

;;    ["/accession" {:name :accession/index}]
;;    ["/item" {:name :item/index}]
;;    ["/taxon" {:name :taxon/index
;;               :handler taxon/index}]
;;    ["/location" {:name :location/index}]
;;    ["/media" {:name :media/index}]])

;; ;; (defmethod ig/init-key ::liana [_ opts]
;; ;;   (liana/start opts))

;; ;; (defmethod ig/halt-key! ::liana [_ instance]
;; ;;   (liana/stop instance))

;; (defn -main [& args]
;;   )
