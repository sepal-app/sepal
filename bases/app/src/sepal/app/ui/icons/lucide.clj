(ns sepal.app.ui.icons.lucide)

(defn contact-round []
  [:svg {:stroke "currentColor",
         :fill "none",
         :stroke-linejoin "round",
         :width "24",
         :xmlns "http://www.w3.org/2000/svg",
         :stroke-linecap "round",
         :stroke-width "2",
         :class "lucide lucide-contact-round-icon lucide-contact-round",
         :viewBox "0 0 24 24",
         :height "24"}
   [:path {:d "M16 2v2"}]
   [:path {:d "M17.915 21a6 6 0 10-12 0"}]
   [:path {:d "M8 2v2"}]
   [:circle {:cx "12" :cy "11" :r "4"}]
   [:rect {:x "3" :y "3" :width "18" :height "18" :rx "2"}]])

(defn settings []
  [:svg {:stroke "currentColor"
         :fill "none"
         :stroke-linejoin "round"
         :width "24"
         :xmlns "http://www.w3.org/2000/svg"
         :stroke-linecap "round"
         :stroke-width "2"
         :viewBox "0 0 24 24"
         :height "24"}
   [:path {:d "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915"}]
   [:circle {:cx "12" :cy "12" :r "3"}]])

(defn x
  "Close/X icon from Lucide."
  [& {:keys [class] :or {class "w-5 h-5"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:path {:d "M18 6 6 18"}]
   [:path {:d "m6 6 12 12"}]])

(defn globe
  "Globe icon from Lucide."
  [& {:keys [class] :or {class "w-4 h-4"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:circle {:cx "12" :cy "12" :r "10"}]
   [:path {:d "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"}]
   [:path {:d "M2 12h20"}]])

(defn user-x
  "User X icon from Lucide (for archive/deactivate user)."
  [& {:keys [class] :or {class "w-4 h-4"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:path {:d "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"}]
   [:circle {:cx "9" :cy "7" :r "4"}]
   [:line {:x1 "17" :x2 "22" :y1 "8" :y2 "13"}]
   [:line {:x1 "22" :x2 "17" :y1 "8" :y2 "13"}]])

(defn user-check
  "User check icon from Lucide (for activate user)."
  [& {:keys [class] :or {class "w-4 h-4"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:path {:d "m16 11 2 2 4-4"}]
   [:path {:d "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"}]
   [:circle {:cx "9" :cy "7" :r "4"}]])

(defn user-plus
  "User plus icon from Lucide (for invite user)."
  [& {:keys [class] :or {class "w-4 h-4"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:path {:d "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"}]
   [:circle {:cx "9" :cy "7" :r "4"}]
   [:line {:x1 "19" :x2 "19" :y1 "8" :y2 "14"}]
   [:line {:x1 "22" :x2 "16" :y1 "11" :y2 "11"}]])

(defn mail
  "Mail icon from Lucide (for resend invitation)."
  [& {:keys [class] :or {class "w-4 h-4"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:path {:d "m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7"}]
   [:rect {:x "2" :y "4" :width "20" :height "16" :rx "2"}]])

(defn info
  "Info icon from Lucide."
  [& {:keys [class] :or {class "w-5 h-5"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:circle {:cx "12" :cy "12" :r "10"}]
   [:path {:d "M12 16v-4"}]
   [:path {:d "M12 8h.01"}]])

(defn triangle-alert
  "Triangle alert icon from Lucide."
  [& {:keys [class] :or {class "w-5 h-5"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:path {:d "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"}]
   [:path {:d "M12 9v4"}]
   [:path {:d "M12 17h.01"}]])

(defn download
  "Download icon from Lucide."
  [& {:keys [class] :or {class "w-4 h-4"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:path {:d "M12 15V3"}]
   [:path {:d "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"}]
   [:path {:d "m7 10 5 5 5-5"}]])

(defn rotate-cw
  "Rotate-cw icon from Lucide."
  [& {:keys [class] :or {class "w-4 h-4"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:path {:d "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8"}]
   [:path {:d "M21 3v5h-5"}]])

(defn plus
  "Plus icon from Lucide."
  [& {:keys [class] :or {class "w-4 h-4"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:path {:d "M5 12h14"}]
   [:path {:d "M12 5v14"}]])

(defn filter-icon
  "Filter icon from Lucide."
  [& {:keys [class] :or {class "w-4 h-4"}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :class class}
   [:polygon {:points "22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"}]])

(defn circle-check
  "Circle check icon from Lucide."
  [& {:keys [size] :or {size 20}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:circle {:cx "12" :cy "12" :r "10"}]
   [:path {:d "m16 9-5.5 5.5L8 12"}]])

;; ── Section rail ──────────────────────────────────────────────────────────
;; The rail's icons, all from Lucide so they share one 24 grid and one stroke
;; weight. :size is for sepal.app.ui.activity, which draws the same vocabulary
;; at its own scale; the rail passes none, because .spl-nav-icon sizes the svg
;; to its own 20px box.

(defn history
  "History icon from Lucide. Activity — a log, not a clock face."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:path {:d "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"}]
   [:path {:d "M3 3v5h5"}]
   [:path {:d "M12 7v5l4 2"}]])

(defn clipboard-list
  "Clipboard-list icon from Lucide. Accessions — the record of a receipt."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:rect {:width "8" :height "4" :x "8" :y "2" :rx "1" :ry "1"}]
   [:path {:d "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"}]
   [:path {:d "M12 11h4"}]
   [:path {:d "M12 16h4"}]
   [:path {:d "M8 11h.01"}]
   [:path {:d "M8 16h.01"}]])

(defn sprout
  "Sprout icon from Lucide. Material — the living plant in the ground."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:path {:d "M14 9.536V7a4 4 0 0 1 4-4h1.5a.5.5 0 0 1 .5.5V5a4 4 0 0 1-4 4 4 4 0 0 0-4 4c0 2 1 3 1 5a5 5 0 0 1-1 3"}]
   [:path {:d "M4 9a5 5 0 0 1 8 4 5 5 0 0 1-8-4"}]
   [:path {:d "M5 21h14"}]])

(defn panel-right
  "Panel-right icon from Lucide. The control that brings the resource panel in
  from the right on a narrow screen."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:rect {:width "18" :height "18" :x "3" :y "3" :rx "2"}]
   [:path {:d "M15 3v18"}]])

(defn lock
  "Lock icon from Lucide. Marks a control that is unavailable rather than
  merely inactive."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:rect {:width "18" :height "11" :x "3" :y "11" :rx "2" :ry "2"}]
   [:path {:d "M7 11V7a5 5 0 0 1 10 0v4"}]])

(defn trees
  "Trees icon from Lucide. Taxa — a taxonomy is a tree, and these are plants,
  so the glyph reads both ways.

  Two trunks filling the box, against Material's low, wide sprout with its
  ground line. The flower it replaced packed a four-lobe blossom, a centre
  circle, a stem and two leaves into the same box and went to mush at the
  rail's 20px."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:path {:d "M10 10v.2A3 3 0 0 1 8.9 16H5a3 3 0 0 1-1-5.8V10a3 3 0 0 1 6 0Z"}]
   [:path {:d "M7 16v6"}]
   [:path {:d "M13 19v3"}]
   [:path {:d "M12 19h8.3a1 1 0 0 0 .7-1.7L18 14h.3a1 1 0 0 0 .7-1.7L16 9h.2a1 1 0 0 0 .8-1.7L13 3l-1.4 1.5"}]])

(defn map-pin
  "Map-pin icon from Lucide. Locations."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:path {:d "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"}]
   [:circle {:cx "12" :cy "10" :r "3"}]])

(defn tag
  "Tag icon from Lucide. Tags."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:path {:d "M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z"}]
   [:circle {:cx "7.5" :cy "7.5" :r ".5" :fill "currentColor"}]])

(defn eye
  "Eye icon from Lucide. Observations — what a curator saw, not paperwork."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:path {:d "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"}]
   [:circle {:cx "12" :cy "12" :r "3"}]])

(defn image
  "Image icon from Lucide. Media."
  [& {:keys [size] :or {size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :viewBox "0 0 24 24"
         :fill "none"
         :stroke "currentColor"
         :stroke-width "2"
         :stroke-linecap "round"
         :stroke-linejoin "round"
         :width size
         :height size}
   [:rect {:width "18" :height "18" :x "3" :y "3" :rx "2" :ry "2"}]
   [:circle {:cx "9" :cy "9" :r "2"}]
   [:path {:d "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"}]])
