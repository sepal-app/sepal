(ns sepal.app.middleware
  (:require [clojure.pprint :as pp]
            [next.jdbc :as jdbc]
            [reitit.core :as r]
            [reitit.ring.middleware.exception :as exception]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [found see-other]]
            [sepal.organization.interface :as org.i]
            [sepal.user.interface :as user.i]))

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
          viewer (user.i/get-by-id db user-id)]
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
      :is-organization-member))

(defn require-org-membership-middleware [handler]
  (fn [{:keys [context path-params ::r/router viewer] :as request}]
    (let [{:keys [db]} context
          org-id (:org-id path-params)]
      (if (organization-member? db org-id (:user/id viewer))
        (-> request
            (assoc-in [:session :organization] (org.i/get-by-id db org-id))
            (handler))
        (see-other router :root)))))

(defn exception-handler [message exception _request & rest]
  (tap> (str "exception-handler rest: " rest))
  (as-> [:div
         [:h1 {:class "text-xl"} message]
         [:pre
          (with-out-str
            (pp/pprint exception))]] $
    (html/root-template :content $)
    (html/render-html $)
    (assoc $ :status 500)))

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
     ::exception/wrap (fn [_handler e request]
                        (exception-handler "wrap" e request)
                        ;; (println "ERROR" (pr-str (:uri request)))
                        ;; (tap> e)
                        ;; (tap> (ex-message e))
                        ;; (tap> (ex-data e))
                        ;; (tap> (ex-cause e))
                        ;; (handler e request)
                        )
     ;; (partial exception-handler "wrap")
     })))
