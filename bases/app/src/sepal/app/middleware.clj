(ns sepal.app.middleware
  (:require [reitit.core :as r]
            ;; [reitit.ring.middleware.exception :as exception]
            [sepal.app.globals :as g]
            [sepal.app.http-response :as http]
            [sepal.database.interface :as db.i]
            [sepal.organization.interface :as org.i]
            [sepal.user.interface :as user.i]))

(defn wrap-context [handler context]
  (fn [request]
    (-> request
        (assoc :context context)
        (handler))))

;; (defn coerce-response [handler]
;;   (fn [request]
;;     (let [response (handler request)]
;;       (cond
;;         (string? response)
;;         {:status 200
;;          :headers {"content-type" "text/html"}
;;          :body (str response)}

;;         :else
;;         response))))

(defn require-viewer
  "Redirects to /login if there are no valid claims in the request."
  [handler]
  (fn [{:keys [context ::r/router session] :as request}]
    (let [{:keys [db]} context
          user-id (:user/id session)
          viewer (when user-id (user.i/get-by-id db user-id))]
      (if viewer
        (binding [g/*viewer* viewer]
          (-> request
              (assoc :viewer viewer)
              (handler)))
        (http/see-other router :auth/login)))))

(defn find-user-org [db user-id organization-id]
  ;; TODO: coerce to and malli Organization schema
  (db.i/execute-one! db {:select :o.*
                         :from [[:organization :o]]
                         :join [[:organization_user :ou]
                                [:= :ou.user_id user-id]]
                         :where [:and
                                 [:= :ou.organization_id organization-id]
                                 [:= :o.id organization-id]]}))

(defn require-org-membership [handler path-param-key]
  ;; TODO: This redirects to root but maybe we should return a 404 or 403 or
  ;; something.
  (fn [{:keys [context path-params ::r/router viewer] :as request}]
    (let [{:keys [db]} context
          org-id (Long/parseLong (get path-params path-param-key))
          org (find-user-org db (:user/id viewer) org-id)]
      (if (some? org)
        (binding [g/*organization* org]
          (-> request
              (assoc-in [:session :organization] org)
              ;; TODO: Remove current-organization
              (assoc-in [:context :current-organization] org)
              (assoc-in [:context :organization] org)
              (handler)))
        (http/see-other router :root)))))

(defn resource-loader
  "Accept a getter function that accepts the request and loads the resource and
  stores it in the request context under the :resource key. "
  [handler getter]
  (fn [request]
    (let [resource (getter request)]
      (tap> (str "resource: " resource))
      (-> request
          (assoc-in [:context :resource] resource)
          (handler)))))

(defn default-loader
  "A default resource loader that accepts a getter, a path param key and an
  optional loader. The getter accepts the database and the value of the path
  parm and returns the resource."
  ([getter path-param-key]
   (default-loader getter path-param-key identity))
  ([getter path-param-key coercer]
   (fn [{:keys [context path-params] :as request}]
     (let [{:keys [db]} context
           id (-> path-params path-param-key coercer)]
       (getter db id)))))

(defn require-resource-org-membership
  "Require a user to be a member of the organization that owns the resource."
  [handler organization-id-key]
  (fn [{:keys [context ::r/router viewer] :as request}]
    (let [{:keys [db resource]} context
          org-id (get resource organization-id-key)
          org (find-user-org db (:user/id viewer) org-id)]
      ;; TODO: This redirects to root but maybe we should return a 404 or 403 or
      ;; something.
      (if (some? org)
        (binding [g/*organization* org]
          (-> request
              (assoc-in [:session :organization] org)
              ;; TODO: Remove current-organization
              (assoc-in [:context :current-organization] org)
              (assoc-in [:context :organization] org)
              (handler)))
        (http/see-other router :root)))))

(defn bind-globals [handler]
  (fn [{:keys [session ::r/router] :as request}]
    (binding [g/*request* request
              g/*router* router
              g/*session* session]
      (handler request))))

;; (defn exception-handler [message exception _request & rest]
;;   (tap> (str "exception-handler rest: " rest))
;;   (tap> (str "exception-handler message: " message))
;;   ;; (tap> (str "exception-handler exception: " message))
;;   ;; request
;;   (as-> [:div
;;          [:h1 {:class "text-xl"} message]
;;          [:pre
;;           (with-out-str
;;             (pp/pprint exception))]] $
;;     (html/root-template :content $)
;;     (html/render-html $)
;;     (assoc $ :status 500)))

;; (def exception-middleware
;;   (exception/create-exception-middleware
;;    (merge
;;     exception/default-handlers
;;     {;; ex-data with :type ::error
;;      ::error (partial exception-handler "error")

;;        ;; ex-data with ::exception or ::failure
;;      ::exception (partial exception-handler "exception")

;;        ;; SQLException and all it's child classes
;;      java.sql.SQLException (partial exception-handler "sql-exception")

;;        ;; override the default handler
;;      ::exception/default (partial exception-handler "default")

;;        ;; print stack-traces for all exceptions
;;      ::exception/wrap (fn [_handler e request]
;;                         (exception-handler "wrap" e request)
;;                         ;; (println "ERROR" (pr-str (:uri request)))
;;                         ;; (tap> e)
;;                         ;; (tap> (ex-message e))
;;                         ;; (tap> (ex-data e))
;;                         ;; (tap> (ex-cause e))
;;                         ;; (handler e request)
;;                         )
;;      ;; (partial exception-handler "wrap")
;;      })))
