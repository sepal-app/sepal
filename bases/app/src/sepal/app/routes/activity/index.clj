(ns sepal.app.routes.activity.index
  (:require [malli.core :as m]
            [malli.util :as mu]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.activity.interface :as activity.i]
            [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.activity :as ui.activity]
            [sepal.app.ui.avatar :as ui.avatar]
            [sepal.app.ui.page :as ui.page]
            [sepal.database.interface :as db.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.location.interface.spec :as location.spec]
            [sepal.material.interface.activity :as material.activity]
            [sepal.material.interface.spec :as material.spec]
            [sepal.store.interface :as store.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.taxon.interface.spec :as taxon.spec]
            [sepal.user.interface.spec :as user.spec]
            [zodiac.core :as z])
  (:import [java.time Duration Instant ZoneId]
           [java.time.format DateTimeFormatter FormatStyle]
           [java.time.temporal ChronoUnit]))

;;; Time formatting helpers

(def ^:private default-timezone (ZoneId/of "UTC"))

(def ^:private full-datetime-formatter
  (DateTimeFormatter/ofLocalizedDateTime FormatStyle/MEDIUM FormatStyle/SHORT))

(defn relative-time
  "Format an Instant as a relative time string (e.g., '2 hours ago', 'yesterday')."
  [^Instant instant]
  (let [now (Instant/now)
        duration (Duration/between instant now)
        minutes (.toMinutes duration)
        hours (.toHours duration)
        days (.toDays duration)]
    (cond
      (< minutes 1) "just now"
      (< minutes 60) (str minutes (if (= minutes 1) " minute ago" " minutes ago"))
      (< hours 24) (str hours (if (= hours 1) " hour ago" " hours ago"))
      (< days 2) "yesterday"
      (< days 7) (str days " days ago")
      (< days 30) (str (quot days 7) (if (= (quot days 7) 1) " week ago" " weeks ago"))
      :else (str days " days ago"))))

(defn format-full-datetime
  "Format an Instant as a full date/time string in the given timezone."
  [^Instant instant ^ZoneId timezone]
  (let [tz (or timezone default-timezone)
        zdt (.atZone instant tz)]
    (.format full-datetime-formatter zdt)))

;;; Legacy components (to be removed after refactor)

(defn timeline-activity [& {:keys [_icon title _description]}]
  [:div {:class "items-center block p-3 sm:flex"}
   [:div {:class "text-gray-600"}
    [:div {:class "text-base font-normal"}
     title]]])

(defmulti activity-description
  (fn [& {:keys [activity]}]
    (:activity/type activity)))

(defmethod activity-description :default [& {:keys []}]
  nil)

(defmethod activity-description accession.activity/created
  [& {:keys [activity]}]
  (let [{:keys [accession taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created accession ")
                               [:a {:class "spl-link"
                                    :href (z/url-for accession.routes/detail
                                                     {:id (:accession/id accession)})}
                                (:accession/code accession)]
                               (when (some? taxon)
                                 [" ("
                                  [:a {:class "spl-link"
                                       :href (z/url-for taxon.routes/detail
                                                        {:id (:taxon/id taxon)})}

                                   (:taxon/name taxon)]
                                  ")"])])))

(defmethod activity-description accession.activity/updated
  [& {:keys [activity]}]
  (let [{:keys [accession taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " updated accession ")
                               [:a {:class "spl-link"
                                    :href (z/url-for accession.routes/detail
                                                     {:id (:accession/id accession)})}
                                (:accession/code accession)]
                               (when (some? taxon)
                                 [" ("
                                  [:a {:class "spl-link"
                                       :href (z/url-for taxon.routes/detail
                                                        {:id (:taxon/id taxon)})}

                                   (:taxon/name taxon)]
                                  ")"])])))

(defmethod activity-description taxon.activity/created
  [& {:keys [activity]}]
  (let [{:keys [parent taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created taxon ")
                               [:a {:class "spl-link"
                                    :href (z/url-for taxon.routes/detail
                                                     {:id (:taxon/id taxon)})}
                                (:taxon/name taxon)]
                               (when (some? parent)
                                 [" ("
                                  [:a {:class "spl-link"
                                       :href (z/url-for taxon.routes/detail
                                                        {:id (:taxon/id parent)})}

                                   (:taxon/name parent)]
                                  ")"])])))

(defmethod activity-description taxon.activity/updated
  [& {:keys [activity]}]
  (let [{:keys [parent taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " updated taxon ")
                               [:a {:class "spl-link"
                                    :href (z/url-for taxon.routes/detail
                                                     {:id (:taxon/id taxon)})}
                                (:taxon/name taxon)]
                               (when (some? parent)
                                 [" ("
                                  [:a {:class "spl-link"
                                       :href (z/url-for taxon.routes/detail
                                                        {:id (:taxon/id parent)})}

                                   (:taxon/name parent)]
                                  ")"])])))

(defmethod activity-description location.activity/created
  [& {:keys [activity]}]
  (let [{:keys [location user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created location ")
                               [:a {:class "spl-link"
                                    :href (z/url-for location.routes/detail
                                                     {:id (:location/id location)})}
                                (cond-> (:location/name location)
                                  (:location/code location)
                                  (str (format " (%s)" (:location/code location))))]])))

(defmethod activity-description location.activity/updated
  [& {:keys [activity]}]
  (let [{:keys [location user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " updated location ")
                               [:a {:class "spl-link"
                                    :href (z/url-for location.routes/detail
                                                     {:id (:location/id location)})}
                                (cond-> (:location/name location)
                                  (:location/code location)
                                  (str (format " (%s)" (:location/code location))))]])))

(defmethod activity-description material.activity/created
  [& {:keys [activity]}]
  (let [{:keys [accession material taxon user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " created material ")
                               [:a {:class "spl-link"
                                    :href (z/url-for material.routes/detail
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
  (let [{:keys [material user]} activity]
    (timeline-activity :title [:span (str (:user/email user) " updated material ")
                               [:a {:class "spl-link"
                                    :href (z/url-for material.routes/detail
                                                     {:id (:material/id material)})}
                                (cond-> (:material/name material)
                                  (:material/code material)
                                  (str (format " (%s)" (:material/code material))))]])))

;;; New activity-data multimethod (returns structured data instead of hiccup)

(defmulti activity-data
  "Returns a map with :resource-type, :resource-name, :resource-url, and :context
   for rendering activity items."
  (fn [activity] (:activity/type activity)))

(defmethod activity-data :default [_activity]
  nil)

(defmethod activity-data accession.activity/created [activity]
  (let [{:keys [accession taxon]} activity]
    {:resource-type :accession
     :resource-name (:accession/code accession)
     :resource-url (z/url-for accession.routes/detail {:id (:accession/id accession)})
     :context (str "Accession" (when taxon (str " • " (:taxon/name taxon))))}))

(defmethod activity-data accession.activity/updated [activity]
  (let [{:keys [accession taxon]} activity]
    {:resource-type :accession
     :resource-name (:accession/code accession)
     :resource-url (z/url-for accession.routes/detail {:id (:accession/id accession)})
     :context (str "Accession" (when taxon (str " • " (:taxon/name taxon))))}))

(defmethod activity-data taxon.activity/created [activity]
  (let [{:keys [taxon parent]} activity]
    {:resource-type :taxon
     :resource-name (:taxon/name taxon)
     :resource-url (z/url-for taxon.routes/detail {:id (:taxon/id taxon)})
     :context (str "Taxon" (when parent (str " • " (:taxon/name parent))))}))

(defmethod activity-data taxon.activity/updated [activity]
  (let [{:keys [taxon parent]} activity]
    {:resource-type :taxon
     :resource-name (:taxon/name taxon)
     :resource-url (z/url-for taxon.routes/detail {:id (:taxon/id taxon)})
     :context (str "Taxon" (when parent (str " • " (:taxon/name parent))))}))

(defmethod activity-data location.activity/created [activity]
  (let [{:keys [location]} activity]
    {:resource-type :location
     :resource-name (:location/name location)
     :resource-url (z/url-for location.routes/detail {:id (:location/id location)})
     :context (str "Location" (when (:location/code location)
                                (str " • " (:location/code location))))}))

(defmethod activity-data location.activity/updated [activity]
  (let [{:keys [location]} activity]
    {:resource-type :location
     :resource-name (:location/name location)
     :resource-url (z/url-for location.routes/detail {:id (:location/id location)})
     :context (str "Location" (when (:location/code location)
                                (str " • " (:location/code location))))}))

(defmethod activity-data material.activity/created [activity]
  (let [{:keys [accession material taxon]} activity]
    {:resource-type :material
     :resource-name (format "%s.%s" (:accession/code accession) (:material/code material))
     :resource-url (z/url-for material.routes/detail {:id (:material/id material)})
     :context (str "Material" (when taxon (str " • " (:taxon/name taxon))))}))

(defmethod activity-data material.activity/updated [activity]
  (let [{:keys [material]} activity]
    {:resource-type :material
     :resource-name (or (:material/name material) (:material/code material))
     :resource-url (z/url-for material.routes/detail {:id (:material/id material)})
     :context "Material"}))

;;; Grouping logic

(defn group-consecutive-by-user
  "Groups consecutive activities by the same user.
   Returns a vector of maps with :user, :time, and :activities keys."
  [activities]
  (reduce
    (fn [groups activity]
      (let [user-id (get-in activity [:user :user/id])
            last-group (peek groups)]
        (if (and last-group (= user-id (get-in last-group [:user :user/id])))
          (update-in groups [(dec (count groups)) :activities] conj activity)
          (conj groups {:user (:user activity)
                        :time (:activity/created-at activity)
                        :activities [activity]}))))
    []
    activities))

;;; New activity components

(defn activity-item
  "Render a single activity item with icon, link, badge, and context."
  [activity]
  (when-let [{:keys [resource-type resource-name resource-url context]}
             (activity-data activity)]
    [:div {:class (html/attr "flex" "items-start" "gap-3" "py-2")}
     ;; Fixed-width icon column
     [:div {:class (html/attr "flex-shrink-0" "w-5" "h-5" "text-base-content/60")}
      (ui.activity/resource-icon resource-type)]
     ;; Flexible content column
     [:div {:class "min-w-0 flex-1"}
      [:div {:class (html/attr "flex" "items-center" "gap-2")}
       [:a {:class "spl-link font-medium"
            :href resource-url}
        resource-name]
       (ui.activity/action-badge (:activity/type activity))]
      [:div {:class "text-sm text-base-content/60"}
       context]]]))

(defn activity-card
  "Render a card grouping activities by a single user."
  [{:keys [user time activities]} timezone]
  [:div {:class (html/attr "card" "bg-base-100" "shadow-sm" "mb-4")}
   [:div {:class (html/attr "card-body" "p-4")}
    ;; Header: avatar + email + time
    [:div {:class (html/attr "flex" "items-center" "justify-between")}
     [:div {:class (html/attr "flex" "items-center" "gap-3")}
      (ui.avatar/avatar :email (:user/email user) :size :sm)
      [:span {:class "font-medium"} (:user/email user)]]
     [:time {:class (html/attr "text-sm" "text-base-content/60")
             :title (format-full-datetime time timezone)}
      (relative-time time)]]
    ;; Divider
    [:div {:class (html/attr "divider" "my-2")}]
    ;; Activity items
    [:div {:class (html/attr "flex" "flex-col")}
     (for [activity activities]
       (activity-item activity))]]])

(defn day-header
  "Render a day section header."
  [date-str]
  [:div {:class (html/attr "text-lg" "font-semibold" "text-base-content" "mb-4" "mt-6"
                           "first:mt-0")}
   date-str])

;;; Legacy timeline-section (kept for reference during migration)

(defn timeline-section [date activity]
  [:div {:class (html/attr "p-5" "mb-4" "rounded-lg" "bg-white" "shadow-sm"
                           "ring-1" "ring-black/5")}
   [:time {:class "text-lg font-semibold text-gray-900 dark:text-white"}
    date]
   [:ol {:class "mt-3 divide-y divider-gray-200 dark:divide-gray-700"}
    (for [item activity]
      [:li item])]])

(def ^:private day-header-formatter
  (DateTimeFormatter/ofPattern "EEEE, MMMM d, yyyy"))

(defn- format-day-header
  "Format an Instant as a day header string (e.g., 'Monday, December 8, 2025')."
  [^Instant instant ^ZoneId timezone]
  (let [today (-> (Instant/now)
                  (.atZone timezone)
                  (.truncatedTo ChronoUnit/DAYS))
        yesterday (.minusDays today 1)
        day (-> instant
                (.atZone timezone)
                (.truncatedTo ChronoUnit/DAYS))]
    (cond
      (.equals day today) "Today"
      (.equals day yesterday) "Yesterday"
      :else (.format day-header-formatter day))))

(defn- next-page-url
  "Generate URL for the next page of activities."
  [page page-size]
  (str (z/url-for :sepal.app.routes.activity.routes/index)
       "?page=" (inc page)
       "&page-size=" page-size))

(defn- infinite-scroll-sentinel
  "Render an invisible sentinel element that triggers loading the next page."
  [next-url]
  [:div {:hx-get next-url
         :hx-trigger "revealed"
         :hx-target "#activity-feed"
         :hx-swap "beforeend"}])

(defn timeline-content
  "Render just the activity content (day sections with cards) without page wrapper.
   Used for both initial render and HTMX partial responses."
  [& {:keys [activity timezone page page-size]
      :or {timezone default-timezone}}]
  (let [activity-by-date (group-by #(.truncatedTo (:activity/created-at %)
                                                  ChronoUnit/DAYS)
                                   activity)
        ;; dates in descending order (most recent first)
        dates (sort #(.isAfter %1 %2) (keys activity-by-date))
        has-more? (= (count activity) page-size)]
    [:div
     (for [date dates]
       (let [day-activities (get activity-by-date date)
             ;; Filter out activities that don't have data
             valid-activities (filter #(some? (activity-data %)) day-activities)
             user-groups (group-consecutive-by-user valid-activities)]
         (when (seq user-groups)
           [:div {:key (str date)}
            (day-header (format-day-header date timezone))
            (for [group user-groups]
              (activity-card group timezone))])))
     (when has-more?
       (infinite-scroll-sentinel (next-page-url page page-size)))]))

(defn timeline
  "Render the activity timeline grouped by day and consecutive user."
  [& {:keys [activity timezone page page-size]
      :or {timezone default-timezone
           page 1
           page-size 25}}]
  [:div {:id "activity-feed"}
   (timeline-content :activity activity
                     :timezone timezone
                     :page page
                     :page-size page-size)])

(defn render [& {:keys [activity page page-size]}]
  (ui.page/page :content (timeline :activity activity
                                   :page page
                                   :page-size page-size)
                :breadcrumbs ["Activity"]))

(defn render-partial
  "Render just the activity content for HTMX requests (no page wrapper)."
  [& {:keys [activity page page-size]}]
  (html/render-partial
    (timeline-content :activity activity
                      :page page
                      :page-size page-size)))

(def Activity
  (-> activity.i/Activity
      (mu/assoc :taxon [:maybe taxon.spec/Taxon])
      (mu/assoc :parent [:maybe (mu/select-keys taxon.spec/Taxon
                                                [:taxon/id
                                                 :taxon/name
                                                 :taxon/rank
                                                 :taxon/author])])
      (mu/assoc :accession [:maybe accession.spec/Accession])
      (mu/assoc :location [:maybe location.spec/Location])
      (mu/assoc :material [:maybe material.spec/Material])
      (mu/assoc :user [:maybe user.spec/User])))

(defn get-activity [db page page-size]
  (let [offset (* page-size (- page 1))]
    (->> (db.i/execute! db {:select [:a.*
                                     :tax.*
                                     :acc.*
                                     :loc.*
                                     :mat.*
                                     :u.id
                                     :u.email
                                     [:parent.id :parent__id]
                                     [:parent.name :parent__name]]
                            :from [[:activity :a]]
                            ;; Cast JSON values to integer for index usage on primary keys
                            :join-by [:inner [[:user :u]
                                              [:= :u.id :a.created_by]]
                                      :left [[:accession :acc]
                                             [:= :acc.id
                                              [[:cast [:->> :a.data "accession-id"] :integer]]]]
                                      :left [[:location :loc]
                                             [:= :loc.id
                                              [[:cast [:->> :a.data "location-id"] :integer]]]]
                                      :left [[:material :mat]
                                             [:= :mat.id
                                              [[:cast [:->> :a.data "material-id"] :integer]]]]
                                      :left [[:taxon :tax]
                                             [:or
                                              [:= :tax.id
                                               [[:cast [:->> :a.data "taxon-id"] :integer]]]
                                              [:= :tax.id :acc.taxon_id]]]
                                      :left [[:taxon :parent]
                                             [:= :parent.id :tax.parent_id]]]
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
         (mapv #(m/decode Activity % store.i/transformer)))))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 25} :int]
   [:q :string]])

(defn handler [& {:keys [::z/context headers query-params]}]
  (let [{:keys [db]} context
        {:keys [page page-size _q]} (params/decode Params query-params)
        activity (get-activity db page page-size)
        htmx-request? (get headers "hx-request")]
    (if htmx-request?
      (render-partial :activity activity
                      :page page
                      :page-size page-size)
      (render :activity activity
              :page page
              :page-size page-size))))
