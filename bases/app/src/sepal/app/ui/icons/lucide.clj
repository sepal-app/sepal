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
   [:path {:d "M17.915 22a6 6 0 0 0-12 0"}]
   [:path {:d "M8 2v2"}]
   [:circle {:cx "12", :cy "12", :r "4"}]
   [:rect {:x "3", :y "4", :width "18", :height "18", :rx "2"}]])

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
   [:path {:d "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"}]
   [:circle {:cx "9" :cy "7" :r "4"}]
   [:polyline {:points "16 11 18 13 22 9"}]])

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
