(ns sepal.app.ui.form
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]))

(def anti-forgery-field-name "__anti-forgery-token")

(def AntiForgeryField
  [(keyword anti-forgery-field-name) :string])

(defn anti-forgery-field []
  [:input {:type "hidden"
           :name anti-forgery-field-name
           :id "__anti-forgery-token"
           :value *anti-forgery-token*}])

(defn field [& {:keys [errors label input name]}]
  ;; TODO: try to get the name from the input attributes
  [:div {:for name
         :class "mb-4"}
   [:label {:for name
            :class "spl-label"}
    label
    [:div {:class "mt-1"}
     input

     (when errors
       [:ul {:class "errors"}
        (for [error errors]
          [:li {:class "text-red-600"} error])])]]])

(defn input-field [& {:keys [id label name required type value errors]}]
  (field :errors errors
         :name name
         :label label
         :input [:input {:name name
                         :id id
                         :required (or required false)
                         :value value
                         :type (or type "text")
                         :class "spl-input"
                         :_class "px-4 py-2 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-md border-gray-300 rounded-md"}])
  #_[:div {:class "mb-4"}
   #_(label :for name
          :label label
          :field)
   [:label {:for name
            :class "spl-label"
            :_class "block text-sm font-medium text-gray-700"}
    label
    [:div {:class "mt-1"}

     (when errors
       [:ul {:class "errors"}
        (for [error errors]
          [:li {:class "text-red-600"} error])])]]])

(defn hidden-field [& {:keys [id name value]}]
  [:input {:name name
           :id id
           :value value
           :type "hidden"}])

(defn textarea-field [& {:keys [errors id label name required value]}]
  [:div {:class "mb-4"}
   [:label {:for name
            :class "spl-label"
            :_class "block text-sm font-medium text-gray-700"}
    label
    [:div  {:class "mt-1"}
     [:textarea {:name name
              :id id
              :required (or required false)
              :value value
              :type (or type "text")
              :class "spl-input"
              :_class "px-4 py-2 shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-md border-gray-300 rounded-md"}]
     (when errors
       [:ul {:class "errors"}
        (for [error errors]
          [:li {:class "text-red-600"} error])])]]]
  )

#_(defn select-field [& {:keys [id label name value options attrs]}]
  (tap> (str "attrs: " attrs))
  [:div {:class "mb-4"}
   [:label {:for name
            :class "spl-label"} label
    [:div  {:class "mt-1"}
     [:select (merge {:name name
                      :id id
                      :value value
                      ;; :class "spl-tom-select mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
                      :class "spl-tom-select"
                      }
                     attrs)
      (for [option options]
        option)]]]])
