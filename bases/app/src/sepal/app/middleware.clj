(ns sepal.app.middleware
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.authorization :as authz]
            [sepal.app.datetime :as datetime]
            [sepal.app.features :as features]
            [sepal.app.flash :as flash]
            [sepal.app.globals :as g]
            [sepal.app.http-response :as http]
            [sepal.app.routes.auth.routes :as auth.routes]
            [sepal.app.routes.dashboard.routes :as dashboard.routes]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :as i18n :refer [tr]]
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

(defn- render-hiccup
  "Render a hiccup response to HTML now. zodiac renders a returned vector only
  after the whole middleware stack has returned, and Chassis realises lazy seqs
  then, so a tr inside a `for` would run after the locale binding is gone."
  [response]
  (if (vector? response)
    (z/html-response response)
    response))

(defn locale
  "Bind the browser's language from Accept-Language, or English. require-viewer
  overrides it with the viewer's saved choice; this is what anonymous pages and
  a viewer with no saved language get."
  [handler]
  (fn [{:keys [headers] :as request}]
    (i18n/with-locale (i18n/resolve-locale (get headers "accept-language"))
      (render-hiccup (handler request)))))

(defn require-viewer
  "Redirects to /login if there are no valid claims in the request.
   Also rejects non-active users (forces logout for archived, invited, or any future status)."
  [handler]
  (fn [{:keys [::z/context session uri cookies] :as request}]
    (let [{:keys [db]} context
          user-id (:user/id session)
          viewer (when user-id (user.i/get-by-id db user-id))]
      (if (and viewer (= :active (:user/status viewer)))
        (let [language (:user/language viewer)
              english? (= i18n/source-locale language)
              catalog (when (and language (not english?)) (i18n/catalog language))]
          (binding [g/*viewer* viewer
                    g/*uri* uri
                    g/*rail-open?* (= "1" (get-in cookies ["spl-rail" :value]))
                    ;; English, when chosen, beats the browser. A saved language
                    ;; whose catalog is gone falls back to the browser's, bound
                    ;; by the locale middleware.
                    i18n/*locale* (cond english? nil catalog language :else i18n/*locale*)
                    i18n/*catalog* (cond english? nil catalog catalog :else i18n/*catalog*)]
            (-> request
                (assoc :viewer viewer)
                (handler)
                (render-hiccup))))
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
                       (log/error e "could not load the resource")
                       (error.i/error :resource-loader/error "Unknown error loading the resource")))]
      (cond
        (error.i/error? resource)
        {:body (chassis/html [:p (tr "There was a problem loading this record.")])
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
     :body (chassis/html [:div {:class "spl-alert spl-alert--danger"}
                          [:span (tr "You don't have permission to perform this action.")]])}
    {:status 403
     :headers {"Content-Type" "text/html"}
     :body (chassis/html [:p (tr "You don't have permission to access this page.")])}))

(defn require-permission
  "Middleware that checks the viewer holds `permission`. When they don't, a GET
   redirects to the `redirect` route, given the request's :id, if one is named;
   anything else gets a 403. Must run after require-viewer."
  ([permission]
   (require-permission permission nil))
  ([permission redirect]
   (fn [handler]
     (fn [{:keys [viewer request-method path-params] :as request}]
       (cond
         (authz/user-has-permission? viewer permission)
         (handler request)

         (and redirect (#{:get :head} request-method))
         (http/found redirect {:id (:id path-params)})

         :else
         (forbidden-response request))))))

(def require-access
  "Middleware compiled from the :permission in each route's data, method data
   winning over route data. Applied once, at the root of the router.

   :public needs nothing. Any other value needs a logged-in viewer who holds
   that permission, and a GET may name a :permission-redirect to go to instead
   of a 403. A route that declares nothing is refused, so a new route fails
   closed. Declare :permission on leaf routes only: reitit merges route data
   into children, so a group's permission would cover every route added under
   it."
  {:name ::require-access
   :compile (fn [{:keys [permission permission-redirect]} _opts]
              (cond
                (= :public permission)
                nil

                (nil? permission)
                (fn [_handler]
                  (fn [{:keys [request-method uri] :as request}]
                    (log/error "Route declares no :permission" request-method uri)
                    (forbidden-response request)))

                :else
                (let [check (require-permission permission permission-redirect)]
                  (fn [handler]
                    (require-viewer (check handler))))))})

(def require-feature
  "Middleware compiled from the :feature in each route's data. When the garden
   has turned that feature off the route answers 404, except a GET or HEAD of a
   route marked :feature-read-only?, which keeps the feature's records reachable
   from activity. :feature may sit on a group, since everything in a feature's
   section belongs to it."
  {:name ::require-feature
   :compile (fn [{:keys [feature feature-read-only?]} _opts]
              (when feature
                (fn [handler]
                  (fn [{:keys [request-method] :as request}]
                    (if (or (features/enabled? feature)
                            (and feature-read-only? (#{:get :head} request-method)))
                      (handler request)
                      (http/not-found))))))})

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
          flash-messages (get-in response [:flash :messages])
          incoming (get-in request [:flash :messages])]
      (cond
        ;; HTMX partial response: inject OOB flash into body
        (and htmx-request?
             (seq flash-messages)
             (not (redirect-response? response))
             (html-response? response)
             (string? (:body response)))
        (-> response
            (update :body str (chassis/html (flash/banner-oob flash-messages)))
            (update :flash dissoc :messages))  ;; Clear from session since we rendered it

        ;; A redirect renders nothing, and a flash survives exactly one
        ;; request, so without this the message dies on the hop. Saving an
        ;; accession redirects to /accession/12/, which redirects again to
        ;; /accession/12/general/ — two requests, and "saved successfully"
        ;; never reached a page. Taxa and material bounce the same way.
        (and (redirect-response? response)
             (seq incoming)
             (not (seq flash-messages)))
        (assoc-in response [:flash :messages] incoming)

        ;; Regular response or redirect: leave flash in session
        :else response))))

(defn wrap-org-settings
  "Middleware that loads organization settings into the request context.
   Currently loads:
   - :timezone - Organization timezone string (defaults to 'UTC')
   - :organization-name - What the garden calls itself, or nil
   - :material-separator - What joins an accession and material code in a
     material's full code. Nil or \"\" is none
   - :disabled-features - The set of features the garden has turned off

   The short name first: it is what a garden picks to be called in passing,
   which is what a browser tab and an email subject want. Nil rather than a
   placeholder, so a caller can leave the part out entirely instead of writing
   the word \"Organization\" into a page title."
  [handler]
  (fn [{:keys [::z/context] :as request}]
    (let [{:keys [db]} context
          timezone (datetime/get-timezone db)
          organization-name (->> ["organization.short_name" "organization.long_name"]
                                 (keep #(settings.i/get-value db %))
                                 (remove str/blank?)
                                 (first))
          material-separator (settings.i/get-value db "codes.material_separator")
          disabled-features (features/disabled db)]
      ;; Bound as well as assoc'd: z/*request* is bound before this runs, so a
      ;; renderer reading the context there would not see either of these.
      (binding [g/*organization-name* organization-name
                g/*disabled-features* disabled-features]
        (-> request
            (assoc-in [::z/context :timezone] timezone)
            (assoc-in [::z/context :organization-name] organization-name)
            (assoc-in [::z/context :material-separator] material-separator)
            (assoc-in [::z/context :disabled-features] disabled-features)
            handler)))))

(defn- setup-excluded-path?
  "Returns true if the path should be excluded from setup redirect.
   Only setup routes, static assets, and health check are excluded.
   Auth routes are NOT excluded. wrap-setup-required lets /login through only
   once an admin exists."
  [path]
  (or (str/starts-with? path "/setup")
      (str/starts-with? path "/static")
      (str/starts-with? path "/assets")
      (= path "/ok")))

(defn wrap-setup-required
  "Middleware that redirects to setup wizard if setup is not complete.
   Excludes setup routes, auth routes, static assets, and health checks."
  [handler]
  (fn [{:keys [::z/context uri] :as request}]
    (let [{:keys [db]} context]
      (if (or (setup-excluded-path? uri)
              (setup.shared/setup-complete? db)
              ;; An admin made before the wizard, such as with the CLI, logs in
              ;; to finish it.
              (and (= uri "/login") (setup.shared/admin-exists? db)))
        (handler request)
        (http/see-other setup.routes/index)))))

(defn require-setup-incomplete
  "Middleware that closes the setup wizard once setup is complete. The wizard
   carries no auth, so after completion a GET redirects to the dashboard and
   any other method is refused."
  [handler]
  (fn [{:keys [::z/context request-method] :as request}]
    (cond
      (not (setup.shared/setup-complete? (:db context)))
      (handler request)

      (#{:get :head} request-method)
      (http/see-other dashboard.routes/index)

      :else
      (forbidden-response request))))

(defn require-setup-admin
  "Middleware for the setup steps after the admin step. The admin step logs in
   the admin it creates, so these steps need an active admin's session. Without
   one, a GET goes back to the admin step and any other method is refused."
  [handler]
  (fn [{:keys [::z/context request-method session] :as request}]
    (let [user (some->> (:user/id session) (user.i/get-by-id (:db context)))]
      (cond
        (and (authz/admin? user) (= :active (:user/status user)))
        (handler request)

        (#{:get :head} request-method)
        (http/see-other setup.routes/admin)

        :else
        (forbidden-response request)))))
