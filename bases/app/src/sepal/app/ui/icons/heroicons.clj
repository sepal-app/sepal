(ns sepal.app.ui.icons.heroicons
  (:require [sepal.app.html :as html]
            [sepal.app.router :refer [url-for]]))

(defn user-circle [& {:keys [color size]
                      :or {color "text-gray-400"
                           size 24}}]
  [:svg
   {:xmlns "http://www.w3.org/2000/svg",
    :fill "currentColor",
    :viewBox "0 0 24 24",
    :width size
    :height size
    :class (str color " group-hover:text-gray-500 mr-4 flex-shrink-0")}
   [:path
    {:fill-rule "evenodd",
     :d "M18.685 19.097A9.723 9.723 0 0021.75 12c0-5.385-4.365-9.75-9.75-9.75S2.25 6.615 2.25 12a9.723 9.723 0 003.065 7.097A9.716 9.716 0 0012 21.75a9.716 9.716 0 006.685-2.653zm-12.54-1.285A7.486 7.486 0 0112 15a7.486 7.486 0 015.855 2.812A8.224 8.224 0 0112 20.25a8.224 8.224 0 01-5.855-2.438zM15.75 9a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0z",
     :clip-rule "evenodd"}]]
  )

(defn outline-clock [& {:keys [color size]
                        :or {color "text-gray-400"
                             size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :stroke-width "1.5"
         :stroke "currentColor"
         :viewBox "0 0 24 24"
         :width size
         :height size
         :class (html/attr color "group-hover:text-gray-500" "flex-shrink-0")}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z"}]])

(defn outline-rectangle-group [& {:keys [color size]
                                  :or {color "text-gray-400"
                                       size 24}}]
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :fill "none"
         :stroke-width "1.5"
         :stroke "currentColor"
         :viewBox "0 0 24 24"
         :width size
         :height size
         :class (html/attr color "group-hover:text-gray-500" "flex-shrink-0")}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :d "M2.25 7.125C2.25 6.504 2.754 6 3.375 6h6c.621 0 1.125.504 1.125 1.125v3.75c0 .621-.504 1.125-1.125 1.125h-6a1.125 1.125 0 01-1.125-1.125v-3.75zM14.25 8.625c0-.621.504-1.125 1.125-1.125h5.25c.621 0 1.125.504 1.125 1.125v8.25c0 .621-.504 1.125-1.125 1.125h-5.25a1.125 1.125 0 01-1.125-1.125v-8.25zM3.75 16.125c0-.621.504-1.125 1.125-1.125h5.25c.621 0 1.125.504 1.125 1.125v2.25c0 .621-.504 1.125-1.125 1.125h-5.25a1.125 1.125 0 01-1.125-1.125v-2.25z"}]])

(defn outline-tag [& {:keys [color size]
                      :or {color "text-gray-400"
                           size 24}}]
  [:svg
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :stroke-width "1.5"
    :stroke "currentColor"
    :viewBox "0 0 24 24"
    :width size
    :height size
    :class (str color " group-hover:text-gray-500 flex-shrink-0")}
   [:path
    {:stroke-linecap "round"
     :stroke-linejoin "round"
     :d
     "M9.568 3H5.25A2.25 2.25 0 003 5.25v4.318c0 .597.237 1.17.659 1.591l9.581 9.581c.699.699 1.78.872 2.607.33a18.095 18.095 0 005.223-5.223c.542-.827.369-1.908-.33-2.607L11.16 3.66A2.25 2.25 0 009.568 3z"}]
   [:path
    {:stroke-linecap "round"
     :stroke-linejoin "round"
     :d "M6 6h.008v.008H6V6z"}]]
  )


(defn outline-map-pin [& {:keys [color size]
                          :or {color "text-gray-400"
                               size 24}}]

  [:svg
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :stroke-width "1.5"
    :stroke "currentColor"
    :viewBox "0 0 24 24"
    :width size
    :height size
    :class (str color " group-hover:text-gray-500 flex-shrink-0")}
   [:path
    {:stroke-linecap "round"
     :stroke-linejoin "round"
     :d "M15 10.5a3 3 0 11-6 0 3 3 0 016 0z"}]
   [:path
    {:stroke-linecap "round"
     :stroke-linejoin "round"
     :d "M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z"}]])


(defn outline-photo [& {:keys [color size]
                        :or {color "text-gray-400"
                             size 24}}]

  [:svg
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "none"
    :stroke-width "1.5"
    :stroke "currentColor"
    :viewBox "0 0 24 24"
    :width size
    :height size
    :class (str color " group-hover:text-gray-500 flex-shrink-0")}
   [:path
    {:stroke-linecap "round"
     :stroke-linejoin "round"
     :d "M2.25 15.75l5.159-5.159a2.25 2.25 0 013.182 0l5.159 5.159m-1.5-1.5l1.409-1.409a2.25 2.25 0 013.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 001.5-1.5V6a1.5 1.5 0 00-1.5-1.5H3.75A1.5 1.5 0 002.25 6v12a1.5 1.5 0 001.5 1.5zm10.5-11.25h.008v.008h-.008V8.25zm.375 0a.375.375 0 11-.75 0 .375.375 0 01.75 0z"}]]

  )

(defn outline-x [& {:keys [color size]
                    :or {color "text-gray-400"
                         size 24}}]
  [:svg
   {:xmlns "http://www.w3.org/2000/svg"
    :fill "currentColor"
    :stroke "currentColor"
    :stroke-width "2"
    :viewBox "0 0 24 24"
    :width size
    :height size
    :class (html/attr color "group-hover:text-gray-500 flex-shrink-0")}
   [:path
    {:stroke-linecap "round"
     :stroke-linejoin "round"
     :d "M6 18L18 6M6 6l12 12"}]])

(defn backwards-left []
  [:svg {:class "h-5 w-5"
         :xmlns "http://www.w3.org/2000/svg"
         :viewbox "0 0 24 24"
         :fill "currentColor"
         :aria-hidden "true"}
   [:path {:fill-rule "evenodd"
           :d "M21 16.811c0 .864-.933 1.405-1.683.977l-7.108-4.062a1.125 1.125 0 010-1.953l7.108-4.062A1.125 1.125 0 0121 8.688v8.123zM11.25 16.811c0 .864-.933 1.405-1.683.977l-7.108-4.062a1.125 1.125 0 010-1.953L9.567 7.71a1.125 1.125 0 011.683.977v8.123z"
           :clip-rule "evenodd"}]])

(defn backwards-right []
  [:svg {:class "h-5 w-5"
         :xmlns "http://www.w3.org/2000/svg"
         :viewbox "0 0 24 24"
         :fill "currentColor"
         :aria-hidden "true"}
   [:path {:fill-rule "evenodd"
           :d "M3 8.688c0-.864.933-1.405 1.683-.977l7.108 4.062a1.125 1.125 0 010 1.953l-7.108 4.062A1.125 1.125 0 013 16.81V8.688zM12.75 8.688c0-.864.933-1.405 1.683-.977l7.108 4.062a1.125 1.125 0 010 1.953l-7.108 4.062a1.125 1.125 0 01-1.683-.977V8.688z"
           :clip-rule "evenodd"}]])

(defn chevron-left []
  [:svg {:class "h-5 w-5"
         :xmlns "http://www.w3.org/2000/svg"
         :viewbox "0 0 20 20"
         :fill "currentColor"
         :aria-hidden "true"}
   [:path {:fill-rule "evenodd"
           :d "M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z"
           :clip-rule "evenodd"}]])

(defn chevron-right []
  [:svg {:class "h-5 w-5"
         :xmlns "http://www.w3.org/2000/svg"
         :viewbox "0 0 20 20"
         :fill "currentColor"
         :aria-hidden "true"}
   [:path {:fill-rule "evenodd"
           :d "M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
           :clip-rule "evenodd"}]])
