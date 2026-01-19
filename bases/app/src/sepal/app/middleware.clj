(ns sepal.app.middleware
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.authorization :as authz]
            [sepal.app.flash :as flash]
            [sepal.app.globals :as g]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.error.interface :as error.i]
            [sepal.settings.interface :as settings.i]
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
  "Redirects to /login if there are no valid claims in the request.
   Also rejects non-active users (forces logout for archived, invited, or any future status)."
  [handler]
  (fn [{:keys [::z/context session] :as request}]
    (let [{:keys [db]} context
          user-id (:user/id session)
          viewer (when user-id (user.i/get-by-id db user-id))]
      (if (and viewer (= :active (:user/status viewer)))
        (binding [g/*viewer* viewer]
          (-> request
              (assoc :viewer viewer)
              (handler)))
        ;; Clear session and redirect to login for non-active/missing users
        (-> (http/see-other auth.routes/login)
            (assoc :session nil))))))

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

(defn- html-response?
  "Returns true if response has text/html content type."
  [response]
  (let [content-type (or (get-in response [:headers "Content-Type"])
                         (get-in response [:headers "content-type"]))]
    (some-> content-type (str/starts-with? "text/html"))))

(defn- redirect-response?
  "Returns true if response is a redirect (3xx or HX-Redirect/HX-Location)."
  [response]
  (let [status (:status response)
        headers (:headers response)]
    (or (and status (<= 300 status 399))
        (contains? headers "HX-Redirect")
        (contains? headers "HX-Location"))))

(defn wrap-flash-messages
  "Middleware that handles flash messages for both regular and HTMX requests.

   For HTMX partial responses (non-redirect), injects flash messages as OOB
   elements into the response body. For redirects and HX-Location/HX-Redirect,
   leaves flash in session for next page load.

   Only injects into HTML responses with string bodies."
  [handler]
  (fn [{:keys [htmx-request?] :as request}]
    (let [response (handler request)
          flash-messages (get-in response [:flash :messages])]
      (if (and htmx-request?
               (seq flash-messages)
               (not (redirect-response? response))
               (html-response? response)
               (string? (:body response)))
        ;; HTMX partial response: inject OOB flash into body
        (-> response
            (update :body str (chassis/html (flash/banner-oob flash-messages)))
            (update :flash dissoc :messages))  ;; Clear from session since we rendered it
        ;; Regular response or redirect: leave flash in session
        response))))

(defn wrap-org-settings
  "Middleware that loads organization settings into the request context.
   Currently loads:
   - :timezone - Organization timezone string (defaults to 'UTC')"
  [handler]
  (fn [{:keys [::z/context] :as request}]
    (let [{:keys [db]} context
          timezone (or (settings.i/get-value db "organization.timezone") "UTC")]
      (-> request
          (assoc-in [::z/context :timezone] timezone)
          handler))))

(defn- setup-excluded-path?
  "Returns true if the path should be excluded from setup redirect."
  [path]
  (or (str/starts-with? path "/setup")
      (str/starts-with? path "/login")
      (str/starts-with? path "/logout")
      (str/starts-with? path "/forgot-password")
      (str/starts-with? path "/reset-password")
      (str/starts-with? path "/accept-invitation")
      (str/starts-with? path "/static")
      (= path "/ok")))

(defn wrap-setup-required
  "Middleware that redirects to setup wizard if setup is not complete.
   Excludes setup routes, auth routes, static assets, and health checks."
  [handler]
  (fn [{:keys [::z/context uri] :as request}]
    (let [{:keys [db]} context]
      (if (or (setup-excluded-path? uri)
              (setup.shared/setup-complete? db))
        (handler request)
        (http/see-other setup.routes/index)))))
