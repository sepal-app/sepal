(ns sepal.app.ui.avatar
  (:require [clojure.string :as str]
            [sepal.app.html :as html]))

(defn email->initials
  "Extract initials from an email address.
   Takes first 2 characters of the local part (before @) and uppercases them."
  [email]
  (when email
    (let [local-part (first (str/split email #"@"))]
      (-> local-part
          (subs 0 (min 2 (count local-part)))
          str/upper-case))))

(defn avatar
  "Render a DaisyUI avatar with initials placeholder.

   Options:
   - :email - Email address to derive initials from
   - :size - Size keyword: :xs, :sm, :md (default), :lg"
  [& {:keys [email size]
      :or {size :md}}]
  (let [initials (email->initials email)
        size-classes (case size
                       :xs "w-6 text-xs"
                       :sm "w-8 text-sm"
                       :md "w-10 text-base"
                       :lg "w-12 text-lg")]
    [:div {:class (html/attr "avatar" "avatar-placeholder")}
     [:div {:class (html/attr "bg-neutral text-neutral-content rounded-full" size-classes)}
      [:span initials]]]))
