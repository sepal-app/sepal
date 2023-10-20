(ns sepal.app.flash
  (:require [sepal.app.flash.category :as category]
            [sepal.app.html :as html]
            [sepal.app.ui.icons :as icon]))

(defn add-message
  ([response text]
   (add-message response text :info))
  ([response text category]
   (update-in response
              [:flash :messages]
              #(conj % {:text text :category category}))))


(defn error [response text]
  (add-message response text category/error))

#_(defn error-seq
  "Create a validation-seq from humanized malli validation error.

  A validation-seq is a sequence of list of maps with a message key and optional
  field key and :error metadata key is true.
  "
  [error]
  (cond
    ;; Convert map of field errors into array of maps with keys field and message
    (map? error)
    (reduce-kv (fn [acc k v] (conj acc {:field k :messages v})) [] error)

    (string? error)
    {:messages [error]}

    (nil? error)
    {:messages nil}

    ;; Convert sequence of errors into array of maps with a message key
    (seqable? error)
    (map error-seq error)))

(defn set-field-errors
  [response field-errors]
  (assoc-in response
            [:flash :field-errors]
            field-errors))


(defn field-error [request field]
  (get-in request [:flash :field-errors field]))

(defn banner [messages]
  [:div {:class (html/attr "pointer-events-none" "fixed" "inset-x-0" "bottom-0" "sm:flex"
                           "sm:justify-center" "sm:px-6" "sm:pb-5" "lg:px-8")}
   (for [{:keys [text category]} messages]
     (let [color (condp = category
                   category/error :bg-red-600
                   category/success :bg-green-600
                   category/warning :bg-yellow-600
                   :bg-sky-600)]
       [:div {:class (html/attr color "w-full" "pointer-events-auto" "flex" "items-center"
                                "justify-between" "gap-x-6" "" "px-6" "py-2.5" "sm:rounded-xl"
                                "sm:py-3" "sm:pl-4" "sm:pr-3.5" "min-w-[33%]" "text-center")
              :x-data "{show: true}"
              :x-show "show"}
        [:p {:class (html/attr "text-sm" "leading-6" "text-white")}
         [:a {:href "#"}
          [:strong {:class "font-semibold"}
           text]]]
        [:button {:type "button"
                  :class "-m-1.5 flex-none p-1.5
"                  :x-on:click "show=false"}
         [:span {:class "sr-only"} "Dismiss"]
         (icon/outline-x :color "text-white")]]))])
