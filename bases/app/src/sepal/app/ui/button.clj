(ns sepal.app.ui.button)

(defn button [& {:keys [type text]}]
  [:button {:class "inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
            :type (or type "button")}
   text])

(defn link [& {:keys [text href]}]
  [:a {:class "inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
       :href (or href "#")}
   text])
