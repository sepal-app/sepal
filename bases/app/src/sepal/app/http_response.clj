(ns sepal.app.http-response
  (:require [clojure.tools.logging :as log]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.util.http-response :as http]
            [sepal.app.flash :as flash]
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
   errors should be a map of field-name -> [error-messages]

   Also raises a banner. The per-field message can be below the fold on a long
   form, so a rejected save otherwise looked like nothing happened —
   `wrap-flash-messages` swaps this in alongside the field errors.

   `id-suffix` is for a form whose control ids carry one, such as an inline
   edit form repeated per item: each error list id becomes `<field>-<suffix>`,
   matching what `ui.form/input-field` renders for that `:id`."
  [errors & {:keys [id-suffix]}]
  (let [oob-elements (for [[field-name messages] errors]
                       (ui.form/error-list (cond-> (name field-name)
                                             id-suffix (str "-" id-suffix))
                                           messages
                                           :hx-swap-oob? true))]
    (-> {:status 422
         :headers {"Content-Type" "text/html"}
         :body (str (chassis/html (into [:div] oob-elements)))}
        (flash/error "Nothing was saved. Check the highlighted fields."))))

(defn failure-response
  "The response for a failed form post.

   A failure carrying a malli explain becomes a 422 with per-field errors.
   Anything else is logged and becomes `fallback`.

   The discriminator is whether the failure has an explain, not where it came
   from: a store-level coerce failure carries one too, and has to reach the
   user as a field error rather than as a 500. `id-suffix` is passed to
   `validation-errors`."
  [e fallback & {:keys [id-suffix]}]
  (let [err (if (instance? Exception e) (error.i/ex->error e) e)]
    (if-let [errors (error.i/humanize err)]
      (validation-errors errors :id-suffix id-suffix)
      (do (log/error e "form post failed")
          fallback))))

(defn failure-partial
  "Fallback for a handler that answers with an HTML partial. A redirect would
   be wrong for a partial swap, so the message goes back as a 422."
  [e message & {:keys [id-suffix]}]
  (failure-response e (unprocessable-entity [:div {:class "spl-error"} message])
                    :id-suffix id-suffix))

(defn failure-flash
  "Fallback for a handler that answers with a redirect: `response` carrying
   `message` as a flash error.

   The response is passed in rather than a route name because the redirect
   kind genuinely varies across callers -- hx-redirect, see-other and found
   are all in use, and a default would make the odd ones read wrong."
  [e response message]
  (failure-response e (flash/error response message)))

(defn hx-redirect
  "Returns 200 with HX-Redirect header for HTMX client-side redirect.
   Used after successful form submission."
  ([name-or-path]
   (hx-redirect name-or-path nil))
  ([name-or-path args]
   {:status 200
    :headers {"HX-Redirect" (z/url-for name-or-path args)}
    :body ""}))
