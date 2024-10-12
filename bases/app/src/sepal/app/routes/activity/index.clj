(ns sepal.app.routes.activity.index
  (:require [malli.core :as m]
            [malli.util :as mu]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.activity.interface :as activity.i]
            [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.location.interface.spec :as location.spec]
            [sepal.material.interface.activity :as material.activity]
            [sepal.material.interface.spec :as material.spec]
            [sepal.organization.interface.activity :as org.activity]
            [sepal.organization.interface.spec :as org.spec]
            [sepal.store.interface :as store.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.taxon.interface.spec :as taxon.spec]
            [sepal.user.interface.spec :as user.spec]
            [zodiac.core :as z]))

(defn timeline-activity [& {:keys [icon title description]
                            :or {icon (heroicons/user-circle :size 48)}}]
  [:div {:class "items-center block p-3 sm:flex"}
   icon
   [:div {:class "text-gray-600"}
    [:div {:class "text-base font-normal"}
     title]
    [:div {:class "text-sm font-normal"}
     description]]])

(defmulti activity-description
  (fn [& {:keys [activity]}]
    (:activity/type activity)))

(defmethod activity-description :default [& {:keys []}]
  nil)

(defmethod activity-description org.activity/created
  [& {:keys [activity]}]
  (let [{:keys [organization user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created organization ")
                               [:a {:class "spl-link"
                                    :href (z/url-for
                                            :org/detail
                                            {:org-id (:organization/id organization)})}
                                (:organization/name organization)]])))

(defmethod activity-description accession.activity/created
  [& {:keys [activity]}]
  (let [{:keys [accession taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created accession ")
                               [:a {:class "spl-link"
                                    :href (z/url-for
                                            :accession/detail
                                            {:id (:accession/id accession)})}
                                (:accession/code accession)]
                               (when (some? taxon)
                                 [" ("
                                  [:a {:class "spl-link"
                                       :href (z/url-for
                                               :taxon/detail
                                               {:id (:taxon/id taxon)})}

                                   (:taxon/name taxon)]
                                  ")"])])))

(defmethod activity-description accession.activity/updated
  [& {:keys [activity]}]
  (let [{:keys [accession taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " updated accession ")
                               [:a {:class "spl-link"
                                    :href (z/url-for
                                            :accession/detail
                                            {:id (:accession/id accession)})}
                                (:accession/code accession)]
                               (when (some? taxon)
                                 [" ("
                                  [:a {:class "spl-link"
                                       :href (z/url-for
                                               :taxon/detail
                                               {:id (:taxon/id taxon)})}

                                   (:taxon/name taxon)]
                                  ")"])])))

(defmethod activity-description taxon.activity/created
  [& {:keys [activity]}]
  (let [{:keys [parent taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created taxon ")
                               [:a {:class "spl-link"
                                    :href (z/url-for
                                            :taxon/detail
                                            {:id (:taxon/id taxon)})}
                                (:taxon/name taxon)]
                               (when (some? parent)
                                 [" ("
                                  [:a {:class "spl-link"
                                       :href (z/url-for
                                               :taxon/detail
                                               {:id (:taxon/id parent)})}

                                   (:taxon/name parent)]
                                  ")"])])))

(defmethod activity-description taxon.activity/updated
  [& {:keys [activity]}]
  (let [{:keys [parent taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " updated taxon ")
                               [:a {:class "spl-link"
                                    :href (z/url-for
                                            :taxon/detail
                                            {:id (:taxon/id taxon)})}
                                (:taxon/name taxon)]
                               (when (some? parent)
                                 [" ("
                                  [:a {:class "spl-link"
                                       :href (z/url-for
                                               :taxon/detail
                                               {:id (:taxon/id parent)})}

                                   (:taxon/name parent)]
                                  ")"])])))

(defmethod activity-description location.activity/created
  [& {:keys [activity]}]
  (let [{:keys [location user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created location ")
                               [:a {:class "spl-link"
                                    :href (z/url-for
                                            :location/detail
                                            {:id (:location/id location)})}
                                (cond-> (:location/name location)
                                  (:location/code location)
                                  (str (format " (%s)" (:location/code location))))]])))

(defmethod activity-description location.activity/updated
  [& {:keys [activity]}]
  (let [{:keys [location user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " updated location ")
                               [:a {:class "spl-link"
                                    :href (z/url-for
                                            :location/detail
                                            {:id (:location/id location)})}
                                (cond-> (:location/name location)
                                  (:location/code location)
                                  (str (format " (%s)" (:location/code location))))]])))

(defmethod activity-description material.activity/created
  [& {:keys [activity]}]
  (let [{:keys [accession material taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created material ")
                               [:a {:class "spl-link"
                                    :href (z/url-for
                                            :material/detail
                                            {:id (:material/id material)})}

                                (format "%s.%s (%s)"
                                        (:accession/code accession)
                                        (:material/code material)
                                        (:taxon/name taxon))
                                #_(cond-> (:material/name material)
                                    (:material/code material)
                                    (str (format " (%s)" (:material/code material))))]])))

(defmethod activity-description material.activity/updated
  [& {:keys [activity]}]
  (let [{:keys [accession material user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " updated material ")
                               [:a {:class "spl-link"
                                    :href (z/url-for
                                            :material/detail
                                            {:id (:material/id material)})}
                                (cond-> (:material/name material)
                                  (:material/code material)
                                  (str (format " (%s)" (:material/code material))))]])))

(defn timeline-section [date activity]
  [:div {:class (html/attr "p-5" "mb-4" "rounded-lg" "bg-white" "shadow"
                           "ring-1" "ring-black" "ring-opacity-5")}
   [:time {:class "text-lg font-semibold text-gray-900 dark:text-white"}
    date]
   [:ol {:class "mt-3 divide-y divider-gray-200 dark:divide-gray-700"}
    (for [item activity]
      [:li item])]])

;; TODO: We need to store the default timezone of the organization
(def default-timezone
  (java.time.ZoneId/of "UTC"))

(def date-time-formatter (java.time.format.DateTimeFormatter/ofPattern "E, MMM d YYYY"))

(defn timeline [& {:keys [activity]}]
  (let [activity-by-date (group-by #(.truncatedTo (:activity/created-at %)
                                                  java.time.temporal.ChronoUnit/DAYS)
                                   activity)
        ;; dates in descending order (most recent first)
        dates (sort #(.isAfter %1 %2) (keys activity-by-date))]
    (reduce (fn [acc date]
              (let [activity (get activity-by-date date)]
                (conj acc
                      (timeline-section (.format date-time-formatter (.atZone date default-timezone)) ;; "January 13th, 2022"
                                        (reduce (fn [acc cur]
                                                  (if-let [desc (activity-description :activity cur)]
                                                    (conj acc desc)
                                                    acc)
                                                  ;; TODO: On hover show a tooltup with the exact time
                                                  ;; in the org's default timezone
                                                  )
                                                []
                                                activity)))))
            []
            dates)))

(defn render [& {:keys [activity]}]
  (page/page :content [(timeline :activity activity)]
             :page-title "Activity"))

(def Activity
  (-> activity.i/Activity
      (mu/assoc :taxon [:maybe taxon.spec/Taxon])
      (mu/assoc :parent [:maybe (mu/select-keys  taxon.spec/Taxon
                                                 [:taxon/id
                                                  :taxon/name
                                                  :taxon/rank
                                                  :taxon/author])])
      (mu/assoc :accession [:maybe accession.spec/Accession])
      (mu/assoc :location [:maybe location.spec/Location])
      (mu/assoc :material [:maybe material.spec/Material])
      (mu/assoc :user [:maybe user.spec/User])
      (mu/assoc :organization [:maybe org.spec/Organization])))

(defn get-activity [db org page page-size]
  (let [offset (* page-size (- page 1))]
    (->> (db.i/execute! db {:select [:a.*
                                     :tax.*
                                     :acc.*
                                     :loc.*
                                     :mat.*
                                     :u.id
                                     :u.email
                                     :org.*
                                     [:parent.id :parent__id]
                                     [:parent.name :parent__name]]
                            :from [[:activity :a]]
                            :join-by [:inner [[:public.user :u]
                                              [:= :u.id :a.created_by]]
                                      :left [[:accession :acc]
                                             [:=
                                              [:->> :a.data "accession-id"]
                                              [[:cast :acc.id :text]]]]
                                      :left [[:location :loc]
                                             [:=
                                              [:->> :a.data "location-id"]
                                              [[:cast :loc.id :text]]]]
                                      :left [[:material :mat]
                                             [:=
                                              [:->> :a.data "material-id"]
                                              [[:cast :mat.id :text]]]]
                                      :left [[:taxon :tax]
                                             [:or
                                              [:=
                                               [:->> :a.data "taxon-id"]
                                               [[:cast :tax.id :text]]]
                                              [:= :acc.taxon_id :tax.id]]]
                                      :left [[:taxon :parent]
                                             [:= :parent.id :tax.parent_id]]
                                      :left [[:organization :org]
                                             [:=
                                              [:->> :a.data "organization-id"]
                                              [[:cast :org.id :text]]]]]
                            :order-by [[:a.created_at :desc]]
                            :where [:= :a.organization_id (:organization/id org)]
                            :offset offset
                            :limit page-size})
         (mapv #(reduce-kv (fn [acc k v]
                             (cond
                               (= (namespace k) "activity")
                               (assoc acc k v)

                               (nil? v)
                               acc

                               (= (namespace k) "parent")
                               (assoc-in acc [:parent (keyword "taxon" (name k))] v)

                               :else
                               (assoc-in acc [(keyword (namespace k)) k] v)))
                           {}
                           %))
         ;; We're using m/decode so that decoding doesn't throw an
         ;; error
         (mapv #(m/decode Activity % store.i/transformer)))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]])

(defn handler [& {:keys [::z/context _headers query-params]}]
  (let [{:keys [db organization]} context
        {:keys [page page-size _q]} (params/decode Params query-params)
        activity (get-activity db organization page page-size)]
    ;; TODO: Add an infinite scroll
    (render :activity activity)))
