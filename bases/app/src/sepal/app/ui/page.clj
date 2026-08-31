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
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.base :as base]
            [sepal.app.ui.icons.bootstrap :as bootstrap]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.icons.lucide :as lucide]
            [zodiac.core :as z]))

(defn sidebar-item
  "One entry in the section rail.

  :current? marks the section the request is in. It emits aria-current=page as
  well as the visual treatment — previously this argument was accepted and
  silently dropped, so no item had an active state at all.

  :aria-label is set explicitly because the visible label is display:none while
  the rail is collapsed, and a hidden label supplies no accessible name."
  [& {:keys [href icon label current?]}]
  [:li
   [:a (cond-> {:href href
                :class (cond-> ["spl-nav-item"]
                         current? (conj "spl-nav-item--current"))
                :aria-label label}
         current? (assoc :aria-current "page"))
    [:span {:class "spl-nav-icon" :aria-hidden "true"}
     icon]
    [:span {:class "spl-nav-label"}
     label]]])

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
   [:label {:for "sidebar-drawer-toggle"
            :class "spl-toggle"
            :aria-label "Toggle sections"}
    (sidebar-toggle-icon)]
   (when breadcrumbs
     [:nav {:class "spl-crumbs" :aria-label "Breadcrumb"}
      [:ol
       (for [item (butlast breadcrumbs)]
         [:li item])
       [:li [:span {:class "spl-crumbs-current" :aria-current "page"}
             (last breadcrumbs)]]]])
   [:div {:class "spl-topbar-spacer"}]
   [:div {:class "spl-topbar-actions"}
    page-title-buttons]])

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

(defn- sections []
  [{:label "Activity" :href (z/url-for activity.routes/index)
    :icon (heroicons/outline-clock)}
   {:label "Accessions" :href (z/url-for accession.routes/index)
    :icon (heroicons/outline-rectangle-group)}
   {:label "Material" :href (z/url-for material.routes/index)
    :icon (heroicons/outline-tag)}
   {:label "Taxa" :href (z/url-for taxon.routes/index)
    :icon (bootstrap/flower1)}
   {:label "Locations" :href (z/url-for location.routes/index)
    :icon (heroicons/outline-map-pin)}
   {:label "Media" :href (z/url-for media.routes/index)
    :icon (heroicons/outline-photo)}
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

(defn page [& {:keys [breadcrumbs content flash footer page-title page-title-buttons attrs]}]
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
      [:div {:class "spl-shell"}
       (sidebar)
       [:label {:for "sidebar-drawer-toggle"
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
                   :src (html/static-url "app/ui/page.ts")}]]]]]]))
