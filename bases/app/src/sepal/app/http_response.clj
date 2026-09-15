(ns sepal.app.http-response
  (:require [clojure.tools.logging :as log]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.util.http-response :as http]
            [sepal.app.ui.form :as ui.form]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(defn found
  ([name-or-path]
   (found name-or-path nil))
  ([name-or-path args]
   (http/found (z/url-for name-or-path args))))

(defn see-other
  ([name-or-path]
   (see-other name-or-path nil))
  ([name-or-path args]
   (http/see-other (z/url-for name-or-path args))))

(defn not-found []
  (http/not-found))

(defn unprocessable-entity
  "Returns 422 Unprocessable Entity with HTML body.
   Used for form validation errors with HTMX.
   Accepts hiccup data and renders to HTML string."
  [hiccup]
  {:status 422
   :headers {"Content-Type" "text/html"}
   :body (str (chassis/html hiccup))})

(defn validation-errors
  "Returns 422 with OOB error elements for each field.
   errors should be a map of field-name -> [error-messages]"
  [errors]
  (let [oob-elements (for [[field-name messages] errors]
                       (ui.form/error-list (name field-name)
                                           messages
                                           :hx-swap-oob? true))]
    {:status 422
     :headers {"Content-Type" "text/html"}
     :body (str (chassis/html (into [:div] oob-elements)))}))

(defn failure-response
  "The response for a failed form post.

   A failure carrying a malli explain becomes a 422 with per-field errors.
   Anything else is logged and becomes `fallback`.

   The discriminator is whether the failure has an explain, not where it came
   from: a store-level coerce failure carries one too, and has to reach the
   user as a field error rather than as a 500."
  [e fallback]
  (let [err (if (instance? Exception e) (error.i/ex->error e) e)]
    (if-let [errors (error.i/humanize err)]
      (validation-errors errors)
      (do (log/error e "form post failed")
          fallback))))

(defn hx-redirect
  "Returns 200 with HX-Redirect header for HTMX client-side redirect.
   Used after successful form submission."
  ([name-or-path]
   (hx-redirect name-or-path nil))
  ([name-or-path args]
   {:status 200
    :headers {"HX-Redirect" (z/url-for name-or-path args)}
    :body ""}))
