(ns sepal.app.resources
  (:require [reitit.ring :as r.ring]
            [reitit.core :as r]))

(defn- resource-handler [ns var]
  #(@(-> ns the-ns ns-interns (get var)) %))

(defn make-resource-routes [resource-name ns & {:keys [only
                                                       index new create show edit update destroy]}]
  ;; "/" - GET(index), POST (create)
  ;; "/new" - GET(new)
  ;; "/:id/edit" - GET(edit)
  ;; "/:id" - GET(show), PUT(update), DELETE(destroy)
  (let [routes (if only
                 (set only)
                 #{:index :new :create :show :edit :update :destroy})
        root-handlers (cond-> {}
                        (routes :index)
                        (assoc :get (resource-handler ns 'index-handler))

                        (routes :create)
                        (assoc :post (resource-handler ns 'create-handler)))
        new-handlers (cond-> {}
                       (routes :new)
                       (assoc :get (resource-handler ns 'new-handler)))
        edit-handlers (cond-> {}
                        (routes :edit)
                        (assoc :get (resource-handler ns 'edit-handler)))
        detail-handlers (cond-> {}
                          (routes :show)
                          (assoc :get (resource-handler ns 'show-handler))

                          (routes :update)
                          (assoc :put (resource-handler ns 'update-handler)
                                 :post (resource-handler ns 'update-handler)
                                 :patch (resource-handler ns 'update-handler))

                          (routes :destroy)
                          (assoc :delete (resource-handler ns 'destroy-handler)))]
    (cond-> [(str "/" resource-name)]
      (seq root-handlers)
      (conj ["" (assoc root-handlers :name (keyword resource-name "root"))])

      (seq new-handlers)
      (conj ["/new" (assoc new-handlers :name (keyword resource-name "new"))])

      (seq edit-handlers)
      (conj ["/:id/edit" (assoc edit-handlers :name (keyword resource-name "edit"))])

      (seq detail-handlers)
      (conj ["/:id/" (assoc detail-handlers :name (keyword resource-name "detail"))]))))
