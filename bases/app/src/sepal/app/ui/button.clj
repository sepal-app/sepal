(ns sepal.app.ui.button
  "Buttons and button-shaped links.

  These were Tailwind UI boilerplate hardcoded to bg-indigo-600 with an
  indigo focus ring, in an app themed emerald — one of the four unrelated
  accent families the design replaced with a single brand green."
  (:require [sepal.app.html :as html]
            [sepal.app.ui.tooltip :as tooltip]))

(defn button [& {:keys [type text class]}]
  [:button {:class (or class "spl-btn spl-btn--primary")
            :type (or type "button")}
   text])

(defn link [& {:keys [text href class]}]
  [:a {:class (or class "spl-btn spl-btn--primary")
       :href (or href "#")}
   text])

(defn icon-button
  "An icon-only button with its label as `aria-label` and a tooltip.
  `danger?` gives it the danger hover, for a destructive action."
  [& {:keys [icon label danger? attrs]}]
  (tooltip/wrap
    [:button (merge {:type "button"
                     :class (html/attr "spl-icon-btn" (when danger? "spl-icon-btn--danger"))
                     :aria-label label}
                    attrs)
     icon]
    label))
