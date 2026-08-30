(ns sepal.app.routes.activity.index
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.util :as mu]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.activity.interface :as activity.i]
            [sepal.app.authorization :as authz]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.routes.setup.activity :as setup.activity]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.activity :as ui.activity]
            [sepal.app.ui.avatar :as ui.avatar]
            [sepal.app.ui.icons.heroicons :as heroicons]
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
  (:import [java.time Instant ZoneId]
           [java.time.format DateTimeFormatter]
           [java.time.temporal ChronoUnit]))

;;; Legacy components (to be removed after refactor)

(defn timeline-activity [& {:keys [_icon title _description]}]
  [:div {:class "items-center block p-3 sm:flex"}
   [:div {:class "text-text-muted"}
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

(defmethod activity-data setup.activity/completed [_activity]
  {:resource-type :setup
   :resource-name "Setup wizard"
   :resource-url nil
   :context "Initial setup completed"})

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

;;; Collapsing a run into a sentence
;;
;; An activity event records who, what type and when — and nothing about what
;; changed, because that was never stored (see plan 025). So four edits to one
;; accession are four identical lines. Collapsing a run of one person's
;; activity into "updated 3 accessions" is what makes the feed readable, and is
;; the whole reason the changelog view was chosen over a raw stream.

(def ^:private resource-nouns
  "Singular and plural for each resource. Taxa, not taxons — a botanist
  notices, and principle 2 says the domain's conventions are correctness."
  {"accession" ["an accession" "accessions"]
   "taxon" ["a taxon" "taxa"]
   "material" ["a material" "materials"]
   "location" ["a location" "locations"]
   "contact" ["a contact" "contacts"]
   "media" ["a media item" "media items"]
   "setup" ["setup" "setup"]
   "settings" ["settings" "settings"]})

(defn- noun [resource n]
  (let [[singular plural] (get resource-nouns resource [(str "a " resource)
                                                        (str resource "s")])]
    (if (= 1 n) singular (str n " " plural))))

(defn- join-clauses [clauses]
  (case (count clauses)
    0 ""
    1 (first clauses)
    2 (str (first clauses) " and " (second clauses))
    (str (str/join ", " (butlast clauses)) " and " (last clauses))))

(defn summarise
  "One sentence for a run of activities by the same person.

  Groups by action and resource, preserving first-seen order so the sentence
  reads in the order things happened: \"updated 2 taxa and deleted an
  accession\"."
  [activities]
  (->> activities
       (map (fn [a]
              (let [t (:activity/type a)]
                [(name t) (namespace t)])))
       (reduce (fn [acc pair]
                 (if (contains? (:seen acc) pair)
                   (update-in acc [:counts pair] inc)
                   (-> acc
                       (update :order conj pair)
                       (update :seen conj pair)
                       (assoc-in [:counts pair] 1))))
               {:order [] :seen #{} :counts {}})
       ((fn [{:keys [order counts]}]
          (for [[action resource :as pair] order]
            (str action " " (noun resource (get counts pair))))))
       (join-clauses)))

;;; New activity components

(defn activity-item
  "Render a single activity item with icon, link, badge, and context."
  [activity]
  (when-let [{:keys [resource-type resource-name resource-url context]}
             (activity-data activity)]
    ;; A chip naming one affected record. The sentence above already says what
    ;; happened, so the chip carries identity and context only.
    [:span {:class "spl-chip" :title context}
     [:span {:class "spl-chip-icon" :aria-hidden "true"}
      (ui.activity/resource-icon resource-type)]
     (if resource-url
       [:a {:class "spl-link" :href resource-url} resource-name]
       [:span resource-name])
     (ui.activity/action-badge (:activity/type activity))]))

(defn activity-card
  "One run of consecutive activity by the same person, written as a sentence
  with the affected records beneath it. Six events become one line and six
  chips rather than six near-identical rows."
  [{:keys [user time activities]} timezone]
  [:div {:class "spl-changelog-entry"}
   [:div {:class "spl-changelog-avatar"}
    (ui.avatar/avatar :email (:user/email user) :size :sm)]
   [:div {:class "spl-changelog-body"}
    [:p {:class "spl-changelog-line"}
     [:span {:class "spl-changelog-actor"} (:user/email user)]
     " "
     (summarise activities)
     " "
     (datetime/relative-time time timezone :class "spl-changelog-time")]
    [:div {:class "spl-changelog-refs"}
     (for [activity activities]
       (activity-item activity))]]])

(defn day-header
  "Render a day section header."
  [date-str]
  [:h2 {:class "spl-changelog-day"} date-str])

(defn empty-state
  "Render what the feed shows when it has nothing to render.

  This is the landing page of a new instance — sepal.app.routes.dashboard.index
  redirects / here — so it says what the page is for and offers the first steps
  the viewer's role allows. A reader can create nothing, so they get the
  explanation alone."
  [viewer]
  [:div {:class (html/attr "spl-card" "bg-surface" "shadow-sm" "mt-6")}
   [:div {:class (html/attr "spl-card-body" "items-center" "text-center" "py-12")}
    [:div {:class "text-text-dim"}
     (heroicons/outline-clock :size 48)]
    [:h2 {:class (html/attr "spl-card-title" "mt-4")}
     "No activity yet"]
    [:p {:class (html/attr "text-text-dim" "max-w-md")}
     "Records created, edited and uploaded by you and your collaborators show up here."]
    (when (authz/can-edit? viewer)
      [:div {:class (html/attr "spl-card-actions" "mt-6")}
       [:a {:class "spl-btn spl-btn--primary"
            :href (z/url-for location.routes/new)}
        "Add a location"]
       [:a {:class "spl-btn spl-btn--ghost"
            :href (z/url-for accession.routes/new)}
        "Add an accession"]])
    (when (authz/user-has-permission? viewer authz/users-create)
      [:div {:class (html/attr "mt-4" "text-sm")}
       [:a {:class "spl-link"
            :href (z/url-for settings.routes/users-invite)}
        "Invite someone to your organization"]])]])

;;; Legacy timeline-section (kept for reference during migration)

(defn timeline-section [date activity]
  [:div {:class (html/attr "p-5" "mb-4" "rounded-lg" "bg-surface" "shadow-sm"
                           "ring-1" "ring-black/5")}
   [:time {:class "text-lg font-semibold text-text dark:text-white"}
    date]
   [:ol {:class "mt-3 divide-y divide-hairline"}
    (for [item activity]
      [:li item])]])

(def ^:private day-header-formatter
  (DateTimeFormatter/ofPattern "EEEE, MMMM d, yyyy"))

(defn- format-day-header
  "Format an Instant as a day header string (e.g., 'Monday, December 8, 2025')."
  [^Instant instant timezone-str]
  (let [timezone (ZoneId/of (or timezone-str "UTC"))
        today (-> (Instant/now)
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
  [& {:keys [activity page page-size timezone viewer]}]
  (let [;; Filter out activities that don't have data. Done before grouping, so
        ;; that a page whose every row lacks an activity-data method counts as
        ;; empty rather than rendering a run of empty day sections.
        renderable (filter #(some? (activity-data %)) activity)
        activity-by-date (group-by #(.truncatedTo (:activity/created-at %)
                                                  ChronoUnit/DAYS)
                                   renderable)
        ;; dates in descending order (most recent first)
        dates (sort #(.isAfter %1 %2) (keys activity-by-date))
        has-more? (= (count activity) page-size)]
    ;; Only on the first page. The infinite-scroll sentinel swaps later pages in
    ;; with beforeend, so without the page check a final page that came back
    ;; empty would append the empty state below a populated feed.
    (if (and (empty? renderable) (= page 1))
      (empty-state viewer)
      [:div
       (for [date dates]
         (let [user-groups (group-consecutive-by-user (get activity-by-date date))]
           [:div {:key (str date)}
            (day-header (format-day-header date timezone))
            (for [group user-groups]
              (activity-card group timezone))]))
       (when has-more?
         (infinite-scroll-sentinel (next-page-url page page-size)))])))

(defn timeline
  "Render the activity timeline grouped by day and consecutive user."
  [& {:keys [activity page page-size timezone viewer]
      :or {page 1
           page-size 25}}]
  [:div {:id "activity-feed"}
   (timeline-content :activity activity
                     :page page
                     :page-size page-size
                     :timezone timezone
                     :viewer viewer)])

(defn render [& {:keys [activity page page-size timezone viewer]}]
  (ui.page/page :content (timeline :activity activity
                                   :page page
                                   :page-size page-size
                                   :timezone timezone
                                   :viewer viewer)
                :breadcrumbs ["Activity"]))

(defn render-partial
  "Render just the activity content for HTMX requests (no page wrapper)."
  [& {:keys [activity page page-size timezone viewer]}]
  (html/render-partial
    (timeline-content :activity activity
                      :page page
                      :page-size page-size
                      :timezone timezone
                      :viewer viewer)))

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

(defn handler [& {:keys [::z/context headers query-params viewer]}]
  (let [{:keys [db timezone]} context
        {:keys [page page-size _q]} (params/decode Params query-params)
        activity (get-activity db page page-size)
        htmx-request? (get headers "hx-request")]
    (if htmx-request?
      (render-partial :activity activity
                      :page page
                      :page-size page-size
                      :timezone timezone
                      :viewer viewer)
      (render :activity activity
              :page page
              :page-size page-size
              :timezone timezone
              :viewer viewer))))
