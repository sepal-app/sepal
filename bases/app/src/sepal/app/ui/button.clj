(ns sepal.app.ui.button
  "Buttons and button-shaped links.

  These were Tailwind UI boilerplate hardcoded to bg-indigo-600 with an
  indigo focus ring, in an app themed emerald — one of the four unrelated
  accent families the design replaced with a single brand green.")

(defn button [& {:keys [type text class]}]
  [:button {:class (or class "spl-btn spl-btn--primary")
            :type (or type "button")}
   text])

(defn link [& {:keys [text href class]}]
  [:a {:class (or class "spl-btn spl-btn--primary")
       :href (or href "#")}
   text])
