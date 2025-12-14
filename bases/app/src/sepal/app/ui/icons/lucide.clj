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
