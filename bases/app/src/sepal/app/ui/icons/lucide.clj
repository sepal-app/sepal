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
