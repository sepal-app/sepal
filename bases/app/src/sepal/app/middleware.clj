(ns sepal.app.middleware
  (:require [clojure.tools.logging :as log]
            [sepal.app.authorization :as authz]
            [sepal.app.globals :as g]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.error.interface :as error.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(defn htmx-request [handler]
  (fn [{:keys [headers] :as request}]
    (-> request
        (assoc :htmx-request?
               (= (get headers "hx-request") "true"))
        (assoc :htmx-boosted?
               (= (get headers "hx-boosted") "true"))
        (handler))))

(defn require-viewer
  "Redirects to /login if there are no valid claims in the request."
  [handler]
  (fn [{:keys [::z/context session] :as request}]
    (let [{:keys [db]} context
          user-id (:user/id session)
          viewer (when user-id (user.i/get-by-id db user-id))]
      (if viewer
        (binding [g/*viewer* viewer]
          (-> request
              (assoc :viewer viewer)
              (handler)))
        (http/see-other auth.routes/login)))))

(defn resource-loader
  "Accept a getter function that accepts the request and loads the resource and
  stores it in the request context under the :resource key. "
  [handler getter]
  (fn [request]
    (let [resource (try
                     (getter request)
                     (catch Exception e
                       (log/error e)
                       (error.i/error :resource-loader/error "Unknown error loading the resource")))]
      (cond
        (error.i/error? resource)
        {:body "ERROR: There was a problem loading the resource"
         :status 500
         :headers {"content-type" "text/html"}}

        (nil? resource)
        (http/not-found)

        :else
        (-> request
            (assoc-in [::z/context :resource] resource)
            (handler))))))

(defn default-loader
  "A default resource loader that accepts a getter, a path param key and an
  optional loader. The getter accepts the database and the value of the path
  parm and returns the resource."
  ([getter path-param-key]
   (default-loader getter path-param-key identity))
  ([getter path-param-key coercer]
   (fn [{:keys [::z/context path-params] :as request}]
     (let [{:keys [db]} context
           id (-> path-params path-param-key coercer)]
       (getter db id)))))

(defn- forbidden-response
  "Return 403 response. For HTMX requests, return HTML fragment.
   For regular requests, show 403 page."
  [{:keys [htmx-request?]}]
  (if htmx-request?
    {:status 403
     :headers {"Content-Type" "text/html"}
     :body "<div class=\"alert alert-error\">You don't have permission to perform this action.</div>"}
    {:status 403
     :headers {"Content-Type" "text/html"}
     :body "Forbidden - You don't have permission to access this resource."}))

(defn require-role
  "Middleware that checks if viewer has one of the specified roles.
   Returns 403 if role check fails.
   Must be used after require-viewer middleware."
  [& roles]
  (let [allowed-roles (set roles)]
    (fn [handler]
      (fn [{:keys [viewer] :as request}]
        (if (contains? allowed-roles (:user/role viewer))
          (handler request)
          (forbidden-response request))))))

(defn require-admin
  "Middleware that requires viewer to be an admin."
  [handler]
  ((require-role :admin) handler))

(defn require-editor-or-admin
  "Middleware that requires viewer to be an editor or admin."
  [handler]
  ((require-role :admin :editor) handler))

(defn require-permission
  "Middleware that checks if viewer has a specific permission.
   More granular than role-based checks."
  [permission]
  (fn [handler]
    (fn [{:keys [viewer] :as request}]
      (if (authz/user-has-permission? viewer permission)
        (handler request)
        (forbidden-response request)))))

(defn require-permission-or-redirect
  "Middleware that checks if viewer has a specific permission.
   If not, redirects to a fallback route instead of returning 403.
   redirect-route-fn should be a function that returns the route name."
  [permission redirect-route-fn]
  (fn [handler]
    (fn [{:keys [viewer path-params] :as request}]
      (if (authz/user-has-permission? viewer permission)
        (handler request)
        (http/found (redirect-route-fn) {:id (:id path-params)})))))
