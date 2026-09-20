(ns sepal.app.routes.location.detail.shared
  (:require [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.ui.actions :as ui.actions]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.app.ui.tabs :as ui.tabs]
            [zodiac.core :as z]))

(def general-tab ::general)
(def observations-tab ::observations)

(defn- tab-items [& {:keys [active location]}]
  [(ui.tabs/item "General"
                 {:href (z/url-for location.routes/detail-general {:id (:location/id location)})
                  :active (= active general-tab)})
   (ui.tabs/item "Observations"
                 {:href (z/url-for location.routes/detail-observations {:id (:location/id location)})
                  :active (= active observations-tab)})])

(defn tabs [location active]
  (ui.tabs/tabs {:label "Location sections"
                 :items (tab-items :location location :active active)}))

(defn page [& {:keys [location active body footer]}]
  (pages.record/page
    :name (:location/name location)
    :tabs (tabs location active)
    :body body
    :footer footer))

(defn breadcrumbs [location]
  [[:a {:href (z/url-for location.routes/index)} "Locations"]
   (:location/name location)])

(defn actions
  "The same actions on every one of a location's sections.

  A location that has ever held material cannot be deleted -- the move log
  names it -- so archiving is the only way it leaves the garden, and it
  belongs beside Delete rather than behind it."
  [& {:keys [location]}]
  (let [id (:location/id location)
        archived? (= :archived (:location/status location))]
    (ui.actions/menu
      :archive-url (when-not archived?
                     (z/url-for location.routes/archive {:id id}))
      :unarchive-url (when archived?
                       (z/url-for location.routes/unarchive {:id id}))
      :delete-url (z/url-for location.routes/delete {:id id}))))
