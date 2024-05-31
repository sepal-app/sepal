(ns sepal.app.routes.activity.index
  (:require [malli.core :as m]
            [malli.transform :as mt]
            [malli.util :as mu]
            [reitit.core :as r]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.activity.interface :as activity.i]
            [sepal.app.html :as html]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.organization.interface.spec :as org.spec]
            [sepal.taxon.interface.spec :as taxon.spec]
            [sepal.user.interface.spec :as user.spec]))

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
  (fn [& {:keys [_router activity]}]
    (:activity/type activity))
  :default "DEFAULT")

(defmethod activity-description :organization/created
  [& {:keys [router activity]}]
  (tap> (str ":organization/create: " activity))

  (let [{:keys [organization user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created organization ")
                               [:a {:class "spl-link"
                                    :href (url-for router
                                                   :org/detail
                                                   {:org-id (:organization/id organization)})}
                                (:organization/name organization)]])))

(defmethod activity-description :accession/created
  [& {:keys [router activity]}]
  (let [{:keys [accession taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created accession ")
                               [:a {:class "spl-link"
                                    :href (url-for router
                                                   :accession/detail
                                                   {:id (:accession/id accession)})}
                                (:accession/code accession)]
                               (when (some? taxon)
                                 [:<>
                                  " ("
                                  [:a {:class "spl-link"
                                       :href (url-for router
                                                      :taxon/detail
                                                      {:id (:taxon/id taxon)})}

                                   (:taxon/name taxon)]
                                  ")"])])))

(defmethod activity-description :taxon/updated
  [& {:keys [router activity]}]
  (let [{:keys [parent taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " updated taxon ")
                               [:a {:class "spl-link"
                                    :href (url-for router
                                                   :taxon/detail
                                                   {:id (:taxon/id taxon)})}
                                (:taxon/name taxon)]
                               (when (some? parent)
                                 [:<>
                                  " ("
                                  [:a {:class "spl-link"
                                       :href (url-for router
                                                      :taxon/detail
                                                      {:id (:taxon/id parent)})}

                                   (:taxon/name parent)]
                                  ")"])])))

(defmethod activity-description :taxon/created
  [& {:keys [router activity]}]
  (let [{:keys [parent taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created taxon ")
                               [:a {:class "spl-link"
                                    :href (url-for router
                                                   :taxon/detail
                                                   {:id (:taxon/id taxon)})}
                                (:taxon/name taxon)]
                               (when (some? parent)
                                 [:<>
                                  " ("
                                  [:a {:class "spl-link"
                                       :href (url-for router
                                                      :taxon/detail
                                                      {:id (:taxon/id parent)})}

                                   (:taxon/name parent)]
                                  ")"])])))

(defn timeline-section [date activity]
  [:div {:class (html/attr "p-5" "mb-4" "border" "border-gray-100" "rounded-lg" "bg-white")}
   [:time {:class "text-lg font-semibold text-gray-900 dark:text-white"}
    date]
   [:ol {:class "mt-3 divide-y divider-gray-200 dark:divide-gray-700"}
    (for [item activity]
      [:li item])]])

;; TODO: We need to store the default timezone of the organization
(def default-timezone
  (java.time.ZoneId/of "UTC"))

(def date-time-formatter (java.time.format.DateTimeFormatter/ofPattern "E, MMM d YYYY"))

(defn timeline [& {:keys [router activity]}]
  (let [activity-by-date (group-by #(.truncatedTo (:activity/created-at %)
                                                  java.time.temporal.ChronoUnit/DAYS)
                                   activity)
        ;; dates in descending order (most recent first)
        dates (sort #(.isAfter %1 %2) (keys activity-by-date))]
    (reduce (fn [acc date]
              (let [activity (get activity-by-date date)]
                (tap> (str "date: " date))
                (conj acc
                      [timeline-section (.format date-time-formatter (.atZone date default-timezone)) ;; "January 13th, 2022"
                       (mapv
                        (fn [activity]
                          ;; TODO: On hover show a tooltup with the exact time
                          ;; in the org's default timezone
                          (activity-description :router router
                                                :activity activity))
                        activity)])))
            []
            dates)))

(defn render [& {:keys [activity router]}]
  (-> (page/page :content [(timeline :activity activity
                                     :router router)]
                 :page-title "Activity"
                 :router router)
      (html/render-html)))

(def Activity
  (-> activity.i/Activity
      (mu/assoc :taxon [:maybe taxon.spec/OrganizationTaxon])
      (mu/assoc :parent [:maybe (mu/select-keys  taxon.spec/OrganizationTaxon
                                                 [:taxon/id
                                                  :taxon/name
                                                  :taxon/rank
                                                  :taxon/author])])
      (mu/assoc :accession [:maybe accession.spec/Accession])
      (mu/assoc :user [:maybe user.spec/User])
      (mu/assoc :organization [:maybe org.spec/Organization])))

(defn get-activity [db page page-size]
  (let [offset (* page-size (- page 1))]
    (->> (db.i/execute! db {:select [:a.*
                                     :tax.*
                                     :acc.*
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
                                      :left [[:taxon :tax]
                                             [:=
                                              [:->> :a.data "taxon-id"]
                                              [[:cast :tax.id :text]]]]
                                      :left [[:taxon :parent]
                                             [:= :parent.id :tax.parent_id]]
                                      :left [[:organization :org]
                                             [:=
                                              [:->> :a.data "organization-id"]
                                              [[:cast :org.id :text]]]]]
                            :order-by [[:a.created_at :desc]]
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
         (mapv #(m/decode Activity % db.i/transformer)))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]])

(def params-transformer (mt/transformer
                         (mt/key-transformer {:decode keyword})
                         mt/strip-extra-keys-transformer
                         mt/default-value-transformer
                         mt/string-transformer))

(defn decode-params [schema params]
  (m/decode schema params params-transformer))

(defn handler [& {:keys [context _headers query-params ::r/router uri]}]
  (let [{:keys [db]} context
        {:keys [page page-size _q] :as params} (decode-params Params query-params)
        activity (get-activity db page page-size)]
    (render :activity activity
            :router router)))
