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
  "A round badge standing in for a person, showing their initials.

   One element, so the fill and the initials share a box and cannot disagree
   about where the centre is. The size class carries both the diameter and the
   type size; see .spl-avatar in components.css.

   Options:
   - :email - Email address to derive initials from
   - :size - Size keyword: :xs, :sm, :md (default), :lg"
  [& {:keys [email size]
      :or {size :md}}]
  ;; The size classes are written out rather than built from the keyword so
  ;; that css-contract-test can see them, and so an unknown size throws here
  ;; instead of emitting a class with no rule behind it.
  (let [size-class (case size
                     :xs "spl-avatar--xs"
                     :sm "spl-avatar--sm"
                     :md "spl-avatar--md"
                     :lg "spl-avatar--lg")]
    [:div {:class (html/attr "spl-avatar" "spl-avatar--placeholder" size-class)}
     (email->initials email)]))
