(ns sepal.app.middleware
  (:require [sepal.app.globals :as g]
            [sepal.app.http-response :as http]
            [sepal.error.interface :as error.i]
            [sepal.organization.interface :as org.i]
            [sepal.user.interface :as user.i]
            [taoensso.timbre :as log]
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
        (http/see-other :auth/login)))))

(defn require-org-membership [handler path-param-key]
  ;; TODO: This redirects to root but maybe we should return a 404 or 403 or
  ;; something.
  (fn [{:keys [::z/context path-params viewer] :as request}]
    (let [{:keys [db]} context
          org-id (Long/parseLong (get path-params path-param-key))
          org (org.i/get-user-org db (:user/id viewer))]
      (if (= (:organization/id org) org-id)
        (binding [g/*organization* org]
          (-> request
              (assoc-in [:session :organization] org)
              (assoc-in [::z/context :organization] org)
              (handler)))
        (http/see-other :root)))))

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

(defn require-resource-org-membership
  "Require a user to be a member of the organization that owns the resource."
  [handler organization-id-key]
  (fn [{:keys [::z/context viewer] :as request}]
    (let [{:keys [db resource]} context
          org-id (get resource organization-id-key)
          org (org.i/get-user-org db (:user/id viewer))]
      (cond
        ;; Pass through if the resource doesn't have an organization id
        (nil? org-id)
        (handler request)

        (nil? resource)
        (do
          (log/warn "Could not get the org of the resource: No resource found")
          (handler request))

        (= (:organization/id org) org-id)
        (binding [g/*organization* org]
          (-> request
              (assoc-in [:session :organization] org)
              (assoc-in [::z/context :organization] org)
              (handler)))

        :else
        (http/see-other :root)))))
