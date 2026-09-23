(ns sepal.app.ui.page
  (:require [clojure.string :as str]
            [sepal.app.flash :as flash]
            [sepal.app.globals :as g]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.activity.routes :as activity.routes]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.routes.observation.routes :as observation.routes]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.routes.tag.routes :as tag.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.base :as base]
            [sepal.app.ui.icons.lucide :as lucide]
            [sepal.app.ui.tooltip :as tooltip]
            [zodiac.core :as z]))

(defn sidebar-item
  "One entry in the section rail.

  :current? marks the section the request is in. It emits aria-current=page as
  well as the visual treatment — previously this argument was accepted and
  silently dropped, so no item had an active state at all.

  :aria-label is set explicitly because the visible label is display:none while
  the rail is collapsed, and a hidden label supplies no accessible name. The
  tooltip is the sighted half of the same problem: it repeats the label rather
  than describing it, so it stays aria-hidden and CSS drops it once the rail is
  expanded and the label is visible again."
  [& {:keys [href icon label current?]}]
  [:li
   (tooltip/wrap
     [:a (cond-> {:href href
                  :class (cond-> ["spl-nav-item"]
                           current? (conj "spl-nav-item--current"))
                  :aria-label label}
           current? (assoc :aria-current "page"))
      [:span {:class "spl-nav-icon" :aria-hidden "true"}
       icon]
      [:span {:class "spl-nav-label"}
       label]]
     label
     :side "right")])

(defn sidebar-toggle-icon []
  [:svg {:xmlns "http://www.w3.org/2000/svg",
         :viewBox "0 0 24 24",
         :stroke-linejoin "round",
         :stroke-linecap "round",
         :stroke-width "2",
         :fill "none",
         :stroke "currentColor",
         :class "my-1.5 inline-block size-4"}
   [:path {:d "M4 4m0 2a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z"}]
   [:path {:d "M9 4v16"}]
   [:path {:d "M14 10l2 2l-2 2"}]])

;; TODO: We need a page-inner component so that we have consistent margins on
;; horizontal pages edges, e.g. the form footer lines up with the form fields

(defn page-inner [& children]
  ;; Gutters match the top bar's, so a page's content lines up with the
  ;; breadcrumb above it rather than sitting in a wider inset.
  [:div {:class "spl-inner"}
   children])

(defn navbar [& {:keys [breadcrumbs page-title-buttons]}]
  [:header {:class "spl-topbar"}
   ;; Two controls, one per width, because they toggle different things: the
   ;; rail's width on a wide screen and the drawer on a narrow one. Only one is
   ;; ever displayed, so only one is in the accessibility tree.
   (tooltip/wrap
     [:label {:for "sidebar-drawer-toggle"
              :class "spl-toggle spl-toggle--rail"
              :aria-label "Toggle sections"}
      (sidebar-toggle-icon)]
     "Toggle sections")
   (tooltip/wrap
     [:label {:for "sidebar-mobile-toggle"
              :class "spl-toggle spl-toggle--drawer"
              :aria-label "Toggle sections"}
      (sidebar-toggle-icon)]
     "Toggle sections")
   (when breadcrumbs
     [:nav {:class "spl-crumbs" :aria-label "Breadcrumb"}
      [:ol
       (for [item (butlast breadcrumbs)]
         [:li item])
       [:li [:span {:class "spl-crumbs-current" :aria-current "page"}
             (last breadcrumbs)]]]])
   [:div {:class "spl-topbar-spacer"}]
   [:div {:class "spl-topbar-actions"}
    page-title-buttons
    ;; Brings the resource panel in from the right on a narrow screen. Rendered
    ;; on every page and shown by CSS only where the panel exists, so the navbar
    ;; needs to know nothing about what the page below it is.
    (tooltip/wrap
      [:label {:for "detail-panel-toggle"
               :class "spl-toggle spl-toggle--panel"
               :aria-label "Toggle details"}
       (lucide/panel-right :size 18)]
      "Toggle details"
      :side "bottom")]])

(defn- current-section?
  "A section is current when the request URI sits under its prefix, so a detail
  page such as /accession/12/general/ still marks Accessions. Settings needs an
  explicit prefix because its landing page is /settings/profile while its other
  pages are siblings of that, not children.

  Takes the URI rather than reading the dynamic var directly: the caller
  captures it eagerly, because a lazy seq realised during rendering has already
  escaped the binding. See the comment in `sidebar`."
  [uri prefix]
  (boolean (some-> uri (str/starts-with? prefix))))

(defn- sections
  "The section rail's entries.

   The three record sections run in the order a garden has to create them: a
   taxon before an accession of it, an accession before material from it. That
   is general above specific, which is also how every other hierarchy here
   reads, down to the Taxa list putting a parent above its children.

   Every icon is Lucide, one 24 grid at stroke 2. Taxa and Material both draw
   plants, which Accessions between them keeps apart — trees are two trunks
   filling the box, a sprout is low and wide with a ground line. The flower
   Taxa used to draw sat next to the sprout and read as the same shape at 20px."
  []
  [{:label "Activity" :href (z/url-for activity.routes/index)
    :icon (lucide/history)}
   {:label "Taxa" :href (z/url-for taxon.routes/index)
    :icon (lucide/trees)}
   {:label "Accessions" :href (z/url-for accession.routes/index)
    :icon (lucide/clipboard-list)}
   {:label "Material" :href (z/url-for material.routes/index)
    :icon (lucide/sprout)}
   {:label "Locations" :href (z/url-for location.routes/index)
    :icon (lucide/map-pin)}
   {:label "Observations" :href (z/url-for observation.routes/index)
    :icon (lucide/eye)}
   {:label "Tags" :href (z/url-for tag.routes/index)
    :icon (lucide/tag)}
   {:label "Media" :href (z/url-for media.routes/index)
    :icon (lucide/image)}
   {:label "Contacts" :href (z/url-for contact.routes/index)
    :icon (lucide/contact-round)}])

(defn sidebar []
  ;; Capture the URI here, eagerly. `for` below is lazy and Chassis realises it
  ;; while writing the response — by which point require-viewer's binding has
  ;; unwound and g/*uri* reads nil. Closing over the value is what makes this
  ;; independent of when rendering happens.
  (let [uri g/*uri*]
    [:nav {:class "spl-rail" :aria-label "Sections"}
     [:ul {:class "spl-nav-list"}
      (for [{:keys [label href icon]} (sections)]
        (sidebar-item :label label
                      :href href
                      :icon icon
                      :current? (current-section? uri href)))]
     [:ul {:class "spl-nav-list spl-nav-list--end"}
      (sidebar-item :label "Settings"
                    :href (z/url-for settings.routes/profile)
                    :icon (lucide/settings)
                    :current? (current-section? uri "/settings/"))]]))

(defn record-header
  "The identity band above a record's tab row: its code in the mono face and
  brand green, its name beneath.

  Layout A of the edit-page design. The breadcrumb says where you are; this
  says which record you are looking at, which is what someone arriving from a
  link needs and what a breadcrumb ending in a bare code does not give them."
  [& {:keys [code name]}]
  (when (or code name)
    [:div {:class "spl-record"}
     (when code [:p {:class "spl-record-code"} code])
     (when name [:p {:class "spl-record-name"} name])]))

(defn- crumb-text
  "The words in a breadcrumb item.

  They are hiccup — a link, or a scientific name split into italic and upright
  runs — so this walks for strings rather than assuming one. Attribute maps are
  not vectors and so are never descended into, which keeps hrefs out of it."
  [item]
  (some-> (->> (tree-seq vector? seq item)
               (filter string?)
               (apply str))
          (str/replace #"\s+" " ")
          (str/trim)
          (not-empty)))

(defn document-title
  "What the browser tab says: where you are, then whose garden, then Sepal.

  Most specific first, because a tab truncates from the right and the page is
  what tells two Sepal tabs apart. Any part that is unknown is left out rather
  than filled with a placeholder."
  [& {:keys [breadcrumbs organization-name]}]
  (->> [(crumb-text (last breadcrumbs)) organization-name "Sepal"]
       (remove nil?)
       (str/join " — ")))

(defn page
  "The application shell.

  `flash` defaults to the request's own, so a page cannot forget to show one.
  It used to be passed by hand and eleven of the fourteen callers did not, so
  every record save left its \"saved successfully\" sitting in the session and
  rendered nowhere — the save looked like it had done nothing."
  [& {:keys [breadcrumbs content flash footer page-title page-title-buttons attrs]}]
  (let [flash (or flash (:flash z/*request*))
        organization-name g/*organization-name*]
    (base/html
      [:div (merge {:x-data true} attrs)
       [:div
      ;; The rail's open state is a checkbox its siblings select on — no
      ;; JavaScript, so the shell is correct on first paint. Below 1024px the
      ;; rail is off-canvas over a scrim; above it, pinned. That is the
      ;; behaviour DaisyUI's `drawer lg:drawer-open` provided.
      ;; The checked state is rendered from a cookie, so the rail is already
      ;; expanded on first paint rather than snapping open after a script runs.
      ;; The handler writes the cookie back so the choice survives navigation.
        [:input (cond-> {:id "sidebar-drawer-toggle"
                         :type "checkbox"
                         :class "spl-drawer-toggle"
                         :onchange (str "document.cookie = 'spl-rail=' + "
                                        "(this.checked ? '1' : '0') + "
                                        "'; path=/; max-age=31536000; samesite=lax'")}
                  g/*rail-open?* (assoc :checked "checked"))]
        ;; The off-canvas drawer's own state, and deliberately never rendered
        ;; checked. Below 1024px the checkbox above would mean "drawer open",
        ;; and it is restored from a cookie — so choosing a section left the
        ;; drawer standing open on the page you arrived at. One state is a
        ;; preference that should outlive a page, the other is not.
        [:input {:id "sidebar-mobile-toggle"
                 :type "checkbox"
                 :class "spl-mobile-toggle"}]
        [:div {:class "spl-shell"}
         (sidebar)
         [:label {:for "sidebar-mobile-toggle"
                  :class "spl-scrim"
                  :aria-hidden "true"}]
         [:div {:class "spl-content"}
          (navbar :breadcrumbs breadcrumbs
                  :page-title-buttons page-title-buttons)
          [:main
         ;; No title block unless a page asks for one. The breadcrumb already
         ;; names where you are, and an empty band of margin above every list
         ;; is what made the redesign read as the old layout in new colours.
           (when page-title
             (page-inner
               [:h1 {:class "spl-page-title"} page-title]))

           [:div {:class "spl-main"}
            content]
           [:div {:id "flash-container"}
            (flash/banner (:messages flash))]

           (when footer
             [:div {:id "page-footer"}
              footer])

           [:script {:type "module"
                     :src (html/static-url "app/ui/page.ts")}]]]]]]
      :title (document-title :breadcrumbs breadcrumbs
                             :organization-name organization-name))))
