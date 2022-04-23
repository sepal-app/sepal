(ns sepal.app.middleware
  (:require [honey.sql :as sql]
            [reitit.core :as r]
            [next.jdbc :as jdbc]
            [reitit.ring.middleware.exception :as exception]
            [sepal.app.http-response :refer [found see-other]]))

;; (ns-unalias *ns* 'found)

(defn wrap-context [handler global-context]
  (fn [request]
    (-> request
        (assoc :context global-context)
        handler)))

(defn require-viewer-middleware
  "Redirects to /login if there are no valid claims in the request."
  [handler]
  (fn [{:keys [context ::r/router session] :as request}]
    (let [{:keys [db]} context
          user-id (:user/id session)
          stmt (-> {:select [:*]
                    :from :public.user
                    :where [:= :id user-id]}
                   (sql/format))
          viewer (jdbc/execute-one! db stmt)]
      (if viewer
        (-> request
            (assoc :viewer viewer)
            handler)
        (found router :login)))))

(defn organization-member? [db organization-id user-id]
  (-> db
      (jdbc/execute-one! ["select is_organization_member(?::int, ?::int)"
                          organization-id
                          user-id])
      :is_organization_member))

(defn require-org-membership-middleware [handler]
  (fn [{:keys [context path-params ::r/router viewer] :as request}]
    (let [{:keys [db]} context]
      (if (organization-member? db (:id path-params) (:user/id viewer))
        (handler request)
        (see-other router :root)))))

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
