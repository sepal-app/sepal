(ns sepal.app.routes.taxon.form
  (:require [huff2.core :as h]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.routes.taxon.form :as taxon.form]
            [sepal.app.ui.button :as button]
            ;; [sepal.app.routes.taxon.views.form :as form]
            [sepal.app.ui.form :as form]))

(defn form [& {:keys [action errors org router values]}]
  (tap> (str "values: " values))
  [:div
   [:form {:action action
           :method "POST"
           :id "taxon-form"}

    (form/anti-forgery-field)
    (form/input-field :label "Name"
                      :name "name"
                      :require true
                      :value (:name values)
                      :errors (:name errors))

    (comment
      (require '[sepal.app.globals :as g])
      (url-for g/*router* :org/taxa {:org-id 1})
        ;; (json/write-str {:url "abc/1"} :escape-slash true)
      ;; (h/raw-string (json/write-str {:url "abc/1"}))
      (tap> (format "{\"url\": \"%s\"}" "abc/1"))
      ())

    (let [url (url-for router :org/taxa {:org-id (:organization/id org)})]
      (tap> (str "url: " url))
      (tap> (json/write-str {:url url}))
      (form/field :label "Parent"
                  :for "parent-id"
                  :input [:select {:x-taxon-field (json/js {:url url})
                                   :name "parent-id"
                                   :autocomplete= "off"}
                          (when (:parent-id values)
                            [:option {:value (:parent-id values)}
                             (:parent-name values)])]))

    #_[:div {:class "mb-4"}
       [:label {:for "parent-id"
                :class "spl-label"}
        "Parent"
        [:div {:class "mt-1"}
         [:taxon-field {:url (url-for router :org/taxa {:org-id (:organization/id org)})
                        :taxon-id (:id values)
                        :name "parent-id"
                        :value (:parent-id values)
                        :initial-value (format "{\"id\": %s, \"name\": \"%s\"}"
                                               (:parent-id values)
                                               (:parent-name values))}
          #_[:option {:value (:parent-id values)} (:parent-name values)]]]]]

    #_(form/select-field :label "Rank"
                         :name "rank"
                         :value (:rank values)
                         :required true
                         :options [[:option {:value "family"} "Family"]
                                   [:option {:value "genus"} "Genus"]])

    (button/button :type "submit" :text "Save")]

   [:script {:type "module"
             :src (html/static-url "js/taxon_form.ts")}]
   ;; [:script {:src "https://unpkg.com/vue@3/dist/vue.global.js" }]
   ])
