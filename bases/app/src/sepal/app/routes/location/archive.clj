(ns sepal.app.routes.location.archive
  "Retiring a location, and bringing it back.

  A location that has ever held material can never be deleted: material_change
  names it as the source or the destination of moves that already happened, and
  removing it would leave the move log saying a plant came from nowhere. No
  action clears that, so archiving is the only way such a location leaves the
  garden. It keeps the row, and with it the name the history reads back."
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.archive :as ui.archive]
            [sepal.database.interface :as db.i]
            [sepal.location.interface :as location.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.material.interface :as material.i]
            [zodiac.core :as z]))

(defn blockers
  "What stops this location being archived, as [{:reason kw :count int}].

  Material standing in it, and nothing else. History is the reason to archive
  rather than a reason not to. Plants still here are a different matter: a
  location that has left the pickers but still holds material hides where that
  material is, so it has to be emptied first."
  [db location]
  (->> [(when-let [n (material.i/count-by-location-id db (:location/id location))]
          (when (pos? n) {:reason :material :count n}))]
       (filterv some?)))

(defn- label [location]
  (str "location " (:location/name location)))

(defn- render-dialog [db location]
  (html/render-partial
    (ui.archive/dialog
      :action (z/url-for location.routes/archive {:id (:location/id location)})
      :label (label location)
      :blockers (blockers db location))))

(defn set-status!
  "Move the location to `status` and record it, in one transaction."
  [db location status activity-type changed-by]
  (db.i/with-transaction [tx db]
    (let [updated (location.i/set-status! tx (:location/id location) status)]
      (location.activity/create! tx activity-type changed-by updated)
      updated)))

(defn handler
  "GET renders the confirmation, POST archives.

  The blockers are computed when the dialog is fetched rather than when the
  page was rendered, the same as delete: a location that gained material while
  the page sat open says so rather than offering an archive that would hide it."
  [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource]} context]
    (case request-method
      :post
      (if (seq (blockers db resource))
        (assoc (render-dialog db resource) :status 422)
        (do
          (set-status! db resource :archived location.activity/archived (:user/id viewer))
          (-> (http/see-other location.routes/detail {:id (:location/id resource)})
              (flash/success (str "Archived " (label resource))))))

      (render-dialog db resource))))

(defn unarchive-handler
  "Bringing one back has nothing to check: an active location is the ordinary
  state, and anything that would block archiving is no reason to refuse it."
  [{:keys [::z/context viewer]}]
  (let [{:keys [db resource]} context]
    (set-status! db resource :active location.activity/unarchived (:user/id viewer))
    (-> (http/see-other location.routes/detail {:id (:location/id resource)})
        (flash/success (str "Restored " (label resource))))))
