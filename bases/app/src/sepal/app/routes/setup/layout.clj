(ns sepal.app.routes.setup.layout
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.spec :as spec]
            [zodiac.core :as z]))

(defn steps-indicator
  "Render the DaisyUI steps component showing progress through the wizard."
  [current-step]
  [:ul {:class "spl-steps  w-full"}
   (for [{:keys [id name]} spec/steps]
     [:li {:class (html/attr "spl-step"
                             (when (<= id current-step) "step-primary"))}
      name])])

(defn step-card
  "Render a step card with title, content, and navigation buttons.
   Pass :next-button false to hide the next button entirely."
  [& {:keys [title content back-url next-button] :or {next-button :default}}]
  [:div {:class "spl-card bg-surface border border-border shadow-sm w-full max-w-2xl"}
   [:div {:class "spl-card-body"}
    [:h2 {:class "spl-card-title text-2xl mb-4"} title]
    content
    [:div {:class "spl-card-actions justify-between mt-6"}
     (if back-url
       [:a {:href back-url
            :class "spl-btn spl-btn--ghost"}
        "← Back"]
       [:div]) ; Empty div for spacing
     (case next-button
       :default [:button {:type "submit" :class "spl-btn spl-btn--primary"} "Next →"]
       (nil false) nil  ; Explicitly hide button
       next-button)]]])

(defn layout
  "Minimal app shell layout for the setup wizard."
  [& {:keys [current-step content flash-messages]}]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:http-equiv "x-ua-compatible" :content "ie=edge"}]

    [:title "Sepal Setup"]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:style "[x-cloak] {display: none !important;}"]
    [:link {:rel "stylesheet" :href (html/static-url "app/css/main.css")}]
    [:script "var PREVENT_FUOC_ON_FIREFOX"]]
   [:body {:hx-boost "true"}
    [:div {:x-data true
           :x-cloak true
           :class "min-h-screen bg-surface-alt"}
     ;; Header
     [:header {:class " bg-surface shadow-sm"}
      [:div {:class "flex-1"}
       [:a {:href (z/url-for setup.routes/index)
            :class "spl-btn spl-btn--ghost text-xl"}
        "🌱 Sepal Setup"]]]

     ;; Main content
     [:main {:class "container mx-auto px-4 py-8"}
      ;; Steps indicator
      [:div {:class "mb-8"}
       (steps-indicator current-step)]

      ;; Centered card
      [:div {:class "flex justify-center"}
       content]]

     ;; Flash messages
     (flash/banner flash-messages)

     ;; Setup wizard script
     [:script {:type "module"
               :src (html/static-url "app/routes/setup/setup.ts")}]]]])
