(ns sepal.app.ui.icons)

(defn outline-menu []
  [:svg {:class "h-6 w-6"
         :xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :viewbox "0 0 24 24"
         :stroke "currentColor"
         :aria-hidden "true"}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :stroke-width "2"
           :d "M4 6h16M4 12h16M4 18h16"}]])

(defn outline-x [& {:keys [color size] :or {color "text-gray-400"
                                            size 5}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "currentColor"
         :stroke "currentColor"
         :stroke-width "2"
         :viewBox "0 0 24 24"
         :width size
         :height size
         :class (str color " group-hover:text-gray-500 mr-4 flex-shrink-0")}])
