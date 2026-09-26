(ns sepal.app.ui.media
  (:require [lambdaisland.uri :as uri]
            [sepal.app.html :as html]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.empty :as ui.empty]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [zodiac.core :as z]))

(def sentinel-id
  "The grid's loading indicator. It sits outside the <ul> because the next page
  is appended with `beforeend` — an indicator inside the list would end up
  stranded between pages."
  "media-loading")

(def ^:private prefetch-offset
  "How many items above the last one the next page starts loading, matching the
  tables. Thumbnails are heavier than rows, so starting early matters more."
  3)

(defn media-item [& {:keys [item next-page-url]}]
  ;; TODO: Make sure that item has a :thumbnail-url key
  [:li (cond-> {:class "relative"}
         (some? next-page-url)
         (merge {:hx-get next-page-url
                 ;; `intersect once` rather than `revealed`: htmx's `revealed` listens
                 ;; for window scroll, and this shell is viewport-locked so the window
                 ;; never scrolls. This worked before the redesign and stopped when the
                 ;; panes began scrolling internally.
                 :hx-trigger "intersect once"
                 :hx-target "#media-list"
                 :hx-swap "beforeend"
                 :hx-indicator (str "#" sentinel-id)}))
   [:div {:class (html/attr "group" "aspect-w-10" "aspect-h-7" "block" "w-full"
                            "overflow-hidden" "rounded-lg" "bg-surface-alt" "shadow-sm"
                            "focus-within:ring-2" "focus-within:ring-brand"
                            "focus-within:ring-offset-2")}
    [:a {:href (z/url-for media.routes/detail {:id (:media/id item)})
         :class "inset-0 focus:outline-none"}
     [:img {:class "pointer-events-none h-full w-full object-cover group-hover:opacity-75"
            :src (:thumbnail-url item)}]]]
   (when-let [title (not-empty (:media/title item))]
     [:p {:class "mt-2 truncate text-sm" :title title} title])
   ;; Set only when the item is linked to something other than the record whose
   ;; tab this is.
   (when-let [{:keys [text url]} (:via item)]
     [:p {:class "mt-1 truncate text-sm text-text-soft"}
      "via "
      (if url [:a {:href url :class "spl-link"} text] text)])])

(defn media-list-items [& {:keys [media next-page-url]}]
  ;; Clamped, so a short final page still triggers from its first item rather
  ;; than not at all.
  (let [trigger-idx (max 0 (- (count media) prefetch-offset))]
    (map-indexed (fn [idx m]
                   (media-item :item m
                               :next-page-url (when (= idx trigger-idx)
                                                next-page-url)))
                 media)))

(defn loading-indicator
  "Shown while the next page of thumbnails is in flight. Collapsed to zero
  height when idle so a finished grid has no gap under it."
  []
  [:div {:id sentinel-id
         :class "spl-grid-sentinel"}
   [:span {:class "spl-sentinel-spinner" :aria-hidden "true"}]
   [:span {:class "spl-sentinel-status" :role "status"}
    [:span {:class "spl-sentinel-loading sr-only"} "Loading more media"]]])

(def ^:private sizes
  [["small" "Small"] ["medium" "Medium"] ["large" "Large"]])

(def ^:private size-defaults
  ;; A record's Media tab shares its width with the side panel, so it starts
  ;; with fewer, larger tiles than the media list.
  {:list {:key "spl-media-size-list" :default "small"}
   :record {:key "spl-media-size-record" :default "large"}})

(defn- size-state
  "The grid's size, kept in the browser: a display preference, per kind of
  page, carried across pages and visits."
  [context]
  (let [{:keys [key default]} (size-defaults context)]
    (format "{ size: localStorage.getItem('%s') || '%s', setSize(s) { this.size = s; localStorage.setItem('%s', s) } }"
            key default key)))

(defn- size-control []
  [:div {:class "spl-segmented" :role "group" :aria-label "Thumbnail size"}
   (for [[value label] sizes]
     [:button {:type "button"
               :x-on:click (format "setSize('%s')" value)
               :x-bind:aria-pressed (format "size === '%s'" value)}
      label])])

(defn- media-grid [& {:keys [media next-page-url context]}]
  [:ul {:id "media-list"
        :class "spl-media-grid"
        :data-size (get-in size-defaults [context :default])
        :x-bind:data-size "size"}
   (media-list-items :media media
                     :next-page-url next-page-url)])

(def empty-state-id
  "The id of the page's empty state. The uploader removes it after the first
  upload lands a tile in the list."
  "media-empty")

(defn media-list
  "The empty state, the grid and its loading indicator. The indicator is a
  sibling of the <ul>, not a member: the next page is appended into the <ul>
  with `beforeend`, so anything inside it would be left stranded between
  pages. The grid renders even when empty, because an upload prepends its new
  tile into `#media-list` — a list that only appears once there is media has
  nothing to prepend into."
  [& {:keys [media next-page-url context filters] :or {context :list}}]
  [:div {:x-data (size-state context)}
   ;; `filters` shows even with nothing to show: widening the scope may be what
   ;; finds media.
   (when (or filters (seq media))
     [:div {:class "spl-media-toolbar"}
      [:div filters]
      (when (seq media)
        (size-control))])
   (when (zero? (count media))
     [:div {:id empty-state-id
            :data-media-drop-target "true"}
      (ui.empty/empty-state
        :icon (heroicons/outline-photo :size 48)
        :title "No media yet"
        :body "Photographs of an accession, its material, or the plant in the
                ground show up here. Drag images here, or upload them."
        :actions [[:button {:id "media-empty-upload"
                            :type "button"
                            :class "spl-btn spl-btn--primary"}
                   "Upload"]])])
   (media-grid :media media :next-page-url next-page-url :context context)
   (loading-indicator)])

(defn scope-toggle
  "A checkbox that widens a Media tab from media linked to this record to media
  linked below it too. A GET form, so the choice is in the URL; boosted like the
  tabs, so changing it swaps the content column."
  [& {:keys [action below? hint]}]
  [:form {:method "get"
          :action action
          :hx-boost "true"
          :hx-select ".spl-content"
          :hx-target ".spl-content"
          :hx-swap "outerHTML"}
   [:label {:class "flex items-center gap-2 text-sm cursor-pointer"
            :title hint}
    [:input (cond-> {:type "checkbox"
                     :class "spl-checkbox"
                     :name "scope"
                     :value "below"
                     :onchange "this.form.requestSubmit()"}
              below? (assoc :checked true))]
    [:span "Include related media"]]])

(defn upload-button
  "Opens the uploader. The id is what `x-media-uploader` binds its trigger to,
  so there is one per page.

  This existed four times — commented out here and hand-rolled in each of the
  three media tabs, all in indigo, which is not a colour this app has."
  []
  [:button {:id "upload-button"
            :type "button"
            :class "spl-btn spl-btn--primary"}
   "Upload"])

(defn format-size
  "A byte count as KB or MB, the way a file browser shows it."
  [bytes]
  (when bytes
    (cond
      (< bytes 1024) (str bytes " B")
      (< bytes (* 1024 1024)) (format "%.0f KB" (/ bytes 1024.0))
      :else (format "%.1f MB" (/ bytes 1024.0 1024.0)))))

(defn thumbnail-url
  "Generate a thumbnail URL for a media item."
  ;; 500 rather than the tile's CSS size, so a tile stays sharp on a
  ;; high-density screen and at the larger size a Media tab uses.
  [media-id & {:keys [w h fit] :or {w 500 h 500 fit "crop"}}]
  (str (z/url-for media.routes/transform {:id media-id})
       "?" (uri/map->query-string {:w w :h h :fit fit})))
