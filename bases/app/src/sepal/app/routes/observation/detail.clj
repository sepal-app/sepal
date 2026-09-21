(ns sepal.app.routes.observation.detail
  (:require [sepal.accession.interface :as accession.i]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.observation.routes :as observation.routes]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.resource-panel :as resource-panel]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.observation.interface :as observation.i]
            [zodiac.core :as z]))

(defn- subject
  "The resource an observation is about, resolved from its polymorphic
  resource-type/resource-id -- there is no foreign key to join on. Returns the
  label and href the detail page links the subject with."
  [db observation]
  (case (:observation/resource-type observation)
    :material
    (let [material (material.i/get-by-id db (:observation/resource-id observation))
          accession (accession.i/get-by-id db (:material/accession-id material))]
      {:label (str (:accession/code accession) "." (:material/code material))
       :href (z/url-for material.routes/detail {:id (:material/id material)})})

    :location
    (let [location (location.i/get-by-id db (:observation/resource-id observation))]
      {:label (:location/name location)
       :href (z/url-for location.routes/detail {:id (:location/id location)})})))

(defn- type-label
  [db type]
  (->> (observation.i/list-types db)
       (some #(when (= type (:observation-type/code %)) (:observation-type/label %)))))

(defn- value-label
  [db type value]
  (when value
    (->> (observation.i/list-values db)
         (some #(when (and (= type (:observation-value/type %)) (= value (:observation-value/code %)))
                  (:observation-value/label %))))))

(defn render [& {:keys [observation subject-info type-label value-label]}]
  (ui.page/page
    :breadcrumbs [[:a {:href (z/url-for observation.routes/index)} "Observations"]
                  type-label]
    :content [:div {:class "max-w-2xl mx-auto"}
              (resource-panel/summary-section
                :fields [{:label "Subject"
                          :value [:a {:class "spl-link" :href (:href subject-info)} (:label subject-info)]}
                         {:label "Type" :value type-label}
                         {:label "Value" :value value-label}
                         {:label "Observed" :value (:observation/observed-on observation)}
                         {:label "Next check" :value (:observation/next-check-on observation)}
                         {:label "Observed by" :value (:observation/observer observation)}
                         {:label "Note" :value (:observation/note observation)}])]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [db resource]} context
        type (:observation/type resource)
        value (:observation/value resource)]
    (render :observation resource
            :subject-info (subject db resource)
            :type-label (type-label db type)
            :value-label (value-label db type value))))
