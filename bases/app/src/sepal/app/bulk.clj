(ns sepal.app.bulk
  "What every bulk action shares on the server: the ids it is given, the
  check that they all still exist, and its answer."
  (:require [failjure.core :as f]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [tr trn]]
            [sepal.validation.interface :as validation.i]))

(def max-ids 500)

(defn- as-vector [v]
  (cond (nil? v) v
        (sequential? v) (vec v)
        :else [v]))

(def Ids
  "The `ids` field. A browser sends one value as a string and several as a
  vector."
  [:and
   [:vector {:decode/form as-vector} [:int {:min 1}]]
   [:fn {:decode/form (comp vec distinct)} #(<= 1 (count %) max-ids)]])

(defn- error-response
  "A 422 carrying `message` as an error banner and in each dialog's error
  slot. The banner is under an open dialog's backdrop."
  [message]
  (flash/error (http/unprocessable-entity
                 [:div {:hx-swap-oob "innerHTML:.spl-bulk-error"} message])
               message))

(defn refused [message]
  (http/halt-with (error-response message)))

(defn not-applied
  "The `f/when-failed` answer for a bulk action: field errors when the
  failure carries them, otherwise `message` as `refused` shows it."
  [e message & {:keys [id-suffix]}]
  (http/failure-response e (error-response message) :id-suffix id-suffix))

(defn check-ids [form-params]
  (when (f/failed? (validation.i/validate-form-values [:map [:ids Ids]] form-params))
    (refused (trn "Select at least one row, and no more than %1."
                  "Select at least one row, and no more than %1."
                  max-ids max-ids))))

(defn require-all [db table ids]
  (when (< (db.i/count db {:select [:id] :from [table] :where [:in :id ids]})
           (count ids))
    (refused (tr "Some of the selected records no longer exist. Reload the list and try again."))))

(defn applied [message]
  (flash/success {:status 200
                  :headers {"Content-Type" "text/html"
                            "HX-Trigger" "bulk-applied"}
                  :body ""}
                 message))

(defn result-message [changed-text skipped-text skipped]
  (if (pos? skipped) (str changed-text " " skipped-text) changed-text))
