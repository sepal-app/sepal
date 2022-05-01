(ns sepal.app.routes.taxon.handlers
  (:require ;[sepal.app.routes.taxon.create :as create]
            [sepal.app.routes.taxon.index :as index]
            ;; [sepal.app.routes.taxon.detail :as detail]
            ))

(defn index-handler [req]
  (index/handler req))

;; (defn create-handler [req]
;;   (create/handler req))

;; (defn new-handler [req]
;;   (create/handler req))

;; (defn detail-handler [req]
;;   (detail/handler req))
