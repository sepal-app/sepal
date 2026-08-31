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
     [:img {:class "pointer-events-none object-cover group-hover:opacity-75"
            :src (:thumbnail-url item)}]]]])

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

(defn- media-grid [& {:keys [media next-page-url]}]
  [:ul {:id "media-list"
        :class (html/attr "grid" "grid-cols-2" "gap-x-4" "gap-y-8" "sm:grid-cols-3"
                          "sm:gap-x-6" "lg:grid-cols-4" "xl:gap-x-8")}
   (media-list-items :media media
                     :next-page-url next-page-url)])

(defn media-list
  "The grid plus its loading indicator. The indicator is a sibling, not a
  member: the next page is appended into the <ul> with `beforeend`, so anything
  inside it would be left stranded between pages."
  [& {:keys [media next-page-url]}]
  (if (zero? (count media))
    ;; Outside the <ul>: inside a grid it was laid out as one cell, which is
    ;; why it rendered as a box half the width of the page.
    (ui.empty/empty-state
      :icon (heroicons/outline-photo :size 48)
      :title "No media yet"
      :body "Photographs of an accession, its material, or the plant in the
             ground show up here.")
    (list
      (media-grid :media media :next-page-url next-page-url)
      (loading-indicator))))

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

(defn thumbnail-url
  "Generate a thumbnail URL for a media item."
  [media-id & {:keys [w h fit] :or {w 300 h 300 fit "crop"}}]
  (str (z/url-for media.routes/transform {:id media-id})
       "?" (uri/map->query-string {:w w :h h :fit fit})))
