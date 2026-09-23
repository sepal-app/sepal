(ns sepal.app.routes.activity.index
  (:require [clojure.string :as str]
            [lambdaisland.uri :as uri]
            [malli.core :as m]
            [malli.util :as mu]
            [sepal.accession.interface.activity :as accession.activity]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.activity.interface :as activity.i]
            [sepal.app.authorization :as authz]
            [sepal.app.cli.activity :as import.activity]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.observation.index :as observation.index]
            [sepal.app.routes.observation.routes :as observation.routes]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.routes.setup.activity :as setup.activity]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.activity :as ui.activity]
            [sepal.app.ui.avatar :as ui.avatar]
            [sepal.app.ui.empty :as ui.empty]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.page :as ui.page]
            [sepal.database.interface :as db.i]
            [sepal.location.interface.activity :as location.activity]
            [sepal.location.interface.spec :as location.spec]
            [sepal.material.interface.activity :as material.activity]
            [sepal.material.interface.spec :as material.spec]
            [sepal.observation.interface :as observation.i]
            [sepal.store.interface :as store.i]
            [sepal.taxon.interface.activity :as taxon.activity]
            [sepal.taxon.interface.spec :as taxon.spec]
            [sepal.user.interface.spec :as user.spec]
            [zodiac.core :as z])
  (:import [java.time Instant LocalDate ZoneId]
           [java.time.format DateTimeFormatter]))

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

;; Without this the row is filtered out of the feed entirely -- `renderable`
;; keeps only what `activity-data` answers for -- so the event was written and
;; then shown to nobody.
(defmethod activity-data import.activity/completed [activity]
  (let [counts (get-in activity [:activity/data :counts])
        total (reduce + 0 (vals counts))]
    {:resource-type :import
     :resource-name (format "%,d records" total)
     :resource-url nil
     ;; The per-table counts are the whole payload, and the chip's title is
     ;; the only place they can be read without querying.
     ;; `name`, because activity.data is decoded with keywordized keys on the
     ;; way back out -- the counts go in as "accession" and come out as
     ;; :accession.
     :context (->> counts
                   (sort-by (comp name key))
                   (map (fn [[table n]] (format "%s %d" (name table) n)))
                   (str/join ", "))}))

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
;; changed, because that was never stored. So four edits to one
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
   "settings" ["settings" "settings"]
   "import" ["an import" "imports"]})

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

(defn chip-title
  "What a chip's tooltip says: the record's context, then when it happened.

  A card shows one relative time for a whole run of events, so this is the only
  place an individual record's own timestamp appears."
  [context ^Instant instant timezone]
  (->> [context (datetime/format-datetime-full instant timezone)]
       (remove str/blank?)
       (str/join " \u2022 ")))

(defn activity-item
  "Render a single activity item with icon, link, badge, and context."
  [activity timezone]
  (when-let [{:keys [resource-type resource-name resource-url context]}
             (activity-data activity)]
    ;; A chip naming one affected record. The sentence above already says what
    ;; happened, so the chip carries identity and context only.
    [:span {:class "spl-chip"
            :title (chip-title context (:activity/created-at activity) timezone)}
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
       (activity-item activity timezone))]]])

(defn empty-state
  "Render what the feed shows when it has nothing to render.

  This is the landing page of a new instance — sepal.app.routes.dashboard.index
  redirects / here — so it says what the page is for and offers the first steps
  the viewer's role allows. A reader can create nothing, so they get the
  explanation alone."
  [viewer]
  (ui.empty/empty-state
    :icon (heroicons/outline-clock :size 48)
    :title "No activity yet"
    :body "Records created, edited and uploaded by you and your collaborators
           show up here."
    :actions
    (list
      (when (authz/can-edit? viewer)
        (list
          [:a {:class "spl-btn spl-btn--primary"
               :href (z/url-for location.routes/new)}
           "Add a location"]
          [:a {:class "spl-btn spl-btn--ghost"
               :href (z/url-for accession.routes/new)}
           "Add an accession"]))
      (when (authz/user-has-permission? viewer authz/users-create)
        [:a {:class "spl-link self-center text-sm"
             :href (z/url-for settings.routes/users-invite)}
         "Invite someone to your organization"]))))

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

(defn activity-day
  "The date an activity happened, in the garden's own timezone.

  A LocalDate, not an instant: the feed groups by this and names the group with
  format-day-header, and both must mean the same day. Truncating the instant in
  UTC instead files anything after 20:00 in New York under the previous date."
  [^Instant instant timezone-str]
  (-> instant
      (.atZone (ZoneId/of (or timezone-str "UTC")))
      (.toLocalDate)))

(defn format-day-header
  "Name a day: 'Today', 'Yesterday', or 'Monday, December 8, 2025'.

  Takes the same LocalDate the feed grouped by, so the heading and the group
  cannot disagree."
  [^LocalDate day timezone-str]
  (let [today (LocalDate/now (ZoneId/of (or timezone-str "UTC")))]
    (cond
      (.equals day today) "Today"
      (.equals day (.minusDays today 1)) "Yesterday"
      :else (.format day-header-formatter day))))

(defn day-header
  "A day section's heading, with the date itself in a tooltip.

  'Today' and 'Yesterday' say which section you are in but not which date that
  is. A heading that already reads as a date gets no tooltip."
  [^LocalDate day timezone]
  (let [label (format-day-header day timezone)
        date-text (.format day-header-formatter day)]
    [:h2 (cond-> {:class "spl-changelog-day"}
           (not= label date-text) (assoc :title date-text))
     label]))

(defn parse-day
  "Read a LocalDate off a query parameter, or nil if it is absent or malformed.

  Nil is a valid answer, not a failure: the parameter only suppresses a
  repeated heading, so a bad one costs a duplicate heading and nothing else."
  [s]
  (when-not (str/blank? s)
    (try
      (LocalDate/parse s)
      (catch java.time.format.DateTimeParseException _ nil))))

(defn- next-page-url
  "Generate URL for the next page of activities.

  last-day is the day this page ends on. The next page repeats that day when
  its events straddle the page boundary, and carrying the date is what lets it
  leave the heading off the second time."
  [page page-size ^LocalDate last-day]
  (str (z/url-for :sepal.app.routes.activity.routes/index)
       "?page=" (inc page)
       "&page-size=" page-size
       (when last-day (str "&last-day=" last-day))))

(def ^:private feed-id
  "The one container every page of the feed is appended into."
  "activity-days")

(def ^:private loading-id "activity-loading")

(def ^:private prefetch-offset
  "How many cards above the last one the next page starts loading, matching the
  tables."
  3)

(defn- infinite-scroll-sentinel
  "Render an invisible sentinel element that triggers loading the next page.

  It is emitted `prefetch-offset` cards above the bottom rather than after the
  last one, so the request is usually already in flight by the time the reader
  arrives. htmx's `intersect` takes only `root:` and `threshold:` — there is no
  rootMargin to fire it early — so position in the document is how that margin
  is expressed."
  [next-url]
  [:div {:hx-get next-url
         ;; `intersect once` rather than `revealed`: htmx's `revealed` listens
         ;; for window scroll, and this shell is viewport-locked so the window
         ;; never scrolls. This worked before the redesign and stopped when the
         ;; panes began scrolling internally.
         :hx-trigger "intersect once"
         :hx-target (str "#" feed-id)
         :hx-swap "beforeend"
         :hx-indicator (str "#" loading-id)}])

(defn- loading-indicator
  "Shown while the next page is in flight. It lives outside the feed, which is
  appended into, so it stays put instead of ending up between pages."
  []
  [:div {:id loading-id
         :class "spl-grid-sentinel"}
   [:span {:class "spl-sentinel-spinner" :aria-hidden "true"}]
   [:span {:class "spl-sentinel-status" :role "status"}
    [:span {:class "spl-sentinel-loading sr-only"} "Loading more activity"]]])

(defn timeline-content
  "Render just the activity content (day sections with cards) without page wrapper.
   Used for both initial render and HTMX partial responses."
  [& {:keys [activity appending? last-day page page-size timezone viewer]}]
  (let [;; Filter out activities that don't have data. Done before grouping, so
        ;; that a page whose every row lacks an activity-data method counts as
        ;; empty rather than rendering a run of empty day sections.
        renderable (filter #(some? (activity-data %)) activity)
        activity-by-date (group-by #(activity-day (:activity/created-at %) timezone)
                                   renderable)
        ;; dates in descending order (most recent first)
        dates (sort #(.isAfter %1 %2) (keys activity-by-date))
        has-more? (= (count activity) page-size)]
    ;; Only on the first page. The infinite-scroll sentinel swaps later pages in
    ;; with beforeend, so without the page check a final page that came back
    ;; empty would append the empty state below a populated feed.
    (if (and (empty? renderable) (= page 1))
      (empty-state viewer)
      (let [sections (for [date dates]
                       [date (group-consecutive-by-user (get activity-by-date date))])
            total (reduce + (map (comp count second) sections))
            ;; Clamped, so a short final page still triggers from its first card
            ;; rather than not at all.
            trigger-at (max 0 (- total prefetch-offset))
            sentinel (when has-more?
                       (infinite-scroll-sentinel
                         (next-page-url page page-size (last dates))))
            days
            ;; The running index is what lets the sentinel be placed by its
            ;; position in the whole page rather than within one day.
            (first
              (reduce (fn [[acc i] [date groups]]
                        [(conj acc
                               [:div {:key (str date)}
                                ;; A day whose events straddle a page boundary
                                ;; opens the next page too. Only the first
                                ;; section can repeat the previous page's day,
                                ;; and it keeps its cards either way -- just
                                ;; not a second heading.
                                (when-not (and (empty? acc)
                                               (= date last-day))
                                  (day-header date timezone))
                                (for [[j group] (map-indexed vector groups)]
                                  (list (activity-card group timezone)
                                        (when (= (+ i j) trigger-at) sentinel)))])
                         (+ i (count groups))])
                      [[] 0]
                      sections))]
        ;; A render that is being appended into the feed must not bring a
        ;; second container with it, or the gutter is paid twice and a gap
        ;; opens at every page boundary. Keyed off appending? rather than the
        ;; page number so that loading ?page=2 directly still gets a container.
        (if appending?
          days
          [:div {:id feed-id :class "spl-changelog"} days])))))

(defn timeline
  "Render the activity timeline grouped by day and consecutive user."
  [& {:keys [activity page page-size timezone viewer]
      :or {page 1
           page-size 25}}]
  (list
    [:div {:id "activity-feed"}
     (timeline-content :activity activity
                       :page page
                       :page-size page-size
                       :timezone timezone
                       :viewer viewer)]
    (loading-indicator)))

(defn- overdue-href
  "Where the overdue count links: the observation index, with the same
  `due:<=<today>` term its own Overdue toggle applies -- built from that
  toggle's own term so the two cannot name the filter differently."
  [today]
  (str (z/url-for observation.routes/index)
       "?" (uri/map->query-string {:q (observation.index/overdue-term today)})))

(defn overdue-observations
  "A count of overdue observations, linking to the observation index filtered
  to them. Absent rather than a zero when nothing is overdue -- a dashboard
  that always reads \"0 overdue\" trains people to stop checking it."
  [count today]
  (when (pos? count)
    [:div {:class "spl-alert spl-alert--warning"}
     (lucide/triangle-alert :class "size-4")
     [:a {:class "spl-link"
          :data-overdue-count count
          :href (overdue-href today)}
      (format "%d overdue observation%s" count (if (= count 1) "" "s"))]]))

(defn render [& {:keys [activity overdue-count page page-size timezone today viewer]}]
  (ui.page/page :content (list
                           (overdue-observations overdue-count today)
                           (timeline :activity activity
                                     :page page
                                     :page-size page-size
                                     :timezone timezone
                                     :viewer viewer))
                :breadcrumbs ["Activity"]))

(defn render-partial
  "Render the activity content for the infinite-scroll sentinel, which appends
  it into the feed the first page opened."
  [& {:keys [activity last-day page page-size timezone viewer]}]
  (html/render-partial
    (timeline-content :activity activity
                      :appending? true
                      :last-day last-day
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
                            ;; Each join resolves the event's subject from the
                            ;; indexed resource columns. The extra arms walk the
                            ;; domain rather than the payload: a material event
                            ;; reaches its accession through material.accession_id
                            ;; and its taxon through accession.taxon_id. That is
                            ;; why material is joined before accession, and
                            ;; accession before taxon -- each one references the
                            ;; table above it.
                            :join-by [:inner [[:user :u]
                                              [:= :u.id :a.created_by]]
                                      :left [[:material :mat]
                                             [:and [:= :a.resource_type "material"]
                                              [:= :mat.id :a.resource_id]]]
                                      :left [[:accession :acc]
                                             [:or
                                              [:and [:= :a.resource_type "accession"]
                                               [:= :acc.id :a.resource_id]]
                                              [:= :acc.id :mat.accession_id]]]
                                      :left [[:location :loc]
                                             [:and [:= :a.resource_type "location"]
                                              [:= :loc.id :a.resource_id]]]
                                      :left [[:taxon :tax]
                                             [:or
                                              [:and [:= :a.resource_type "taxon"]
                                               [:= :tax.id :a.resource_id]]
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
   [:last-day {:optional true} [:maybe :string]]
   [:q :string]])

(defn handler [& {:keys [::z/context headers query-params viewer]}]
  (let [{:keys [db timezone]} context
        {:keys [last-day page page-size _q]} (params/decode Params query-params)
        activity (get-activity db page page-size)
        htmx-request? (get headers "hx-request")
        today (str (datetime/today timezone))]
    (if htmx-request?
      (render-partial :activity activity
                      :last-day (parse-day last-day)
                      :page page
                      :page-size page-size
                      :timezone timezone
                      :viewer viewer)
      (render :activity activity
              :overdue-count (observation.i/count-due db today)
              :page page
              :page-size page-size
              :timezone timezone
              :today today
              :viewer viewer))))
