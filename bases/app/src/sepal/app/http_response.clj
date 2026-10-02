(ns sepal.app.http-response
  (:require [clojure.tools.logging :as log]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.util.http-response :as http]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.ui.form :as ui.form]
            [sepal.error.interface :as error.i]
            [sepal.i18n.interface :refer [tr]]
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
        (flash/error (tr "Nothing was saved. Check the highlighted fields.")))))

(defn saved
  "The response to a save on a record page: `page`, the page as its GET renders
  it, which the form's region-swap morphs into the page. `message`, when given,
  is a success flash. `form-saved` fires on the form once the page has
  settled, which is what resets it."
  ([page]
   (saved page nil))
  ([page message]
   (cond-> (assoc-in (html/render-page page) [:headers "HX-Trigger-After-Settle"] "form-saved")
     message (flash/success message))))

(defn field-errors
  "A step for f/attempt-all: nil when `errors` is empty, otherwise a failure
  that failure-response answers with these field errors. `errors` is keyed
  the way validation-errors takes it."
  [errors]
  (when (seq errors)
    (error.i/error ::field-errors nil {:fields errors})))

(defn halt-with
  "A step for f/attempt-all that stops it and answers with `response`, for a
  check whose answer is not an error, such as asking to confirm a code."
  [response]
  (error.i/error ::halt nil {:response response}))

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
    (cond
      (error.i/error? err ::halt)
      (:response (error.i/data err))

      (error.i/error? err ::field-errors)
      (validation-errors (:fields (error.i/data err)) :id-suffix id-suffix)

      :else
      (if-let [errors (error.i/humanize err)]
        (validation-errors errors :id-suffix id-suffix)
        (do (log/error e "form post failed")
            fallback)))))

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

(defn not-saved
  "Fallback for a handler that answers with the page: field errors when the
  failure carries them, otherwise `message` as an error flash on a response
  with no page in it, so nothing on the page is replaced and the form keeps
  what was typed. `id-suffix` is passed to `failure-response`, for a form whose
  field ids are suffixed."
  [e message & {:keys [id-suffix]}]
  (failure-response e (flash/error (unprocessable-entity nil) message)
                    :id-suffix id-suffix))

(defn hx-redirect
  "Returns 200 with HX-Redirect header for HTMX client-side redirect.
   Used after successful form submission."
  ([name-or-path]
   (hx-redirect name-or-path nil))
  ([name-or-path args]
   {:status 200
    :headers {"HX-Redirect" (z/url-for name-or-path args)}
    :body ""}))
