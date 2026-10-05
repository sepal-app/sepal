(ns sepal.app.ui.column-picker
  "The control in a list's last header cell that chooses its columns."
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.json :as json]
            [sepal.app.list-columns :as list-columns]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.i18n.interface :refer [tr]]))

(defn- option [column visible-keys]
  (let [k (name (:key column))
        id (str "column-picker-" k)]
    [:li {:class "spl-col-picker-option"}
     (if (list-columns/hideable? column)
       (list
         [:input {:type "hidden" :name "offered" :value k}]
         [:input {:type "checkbox" :class "spl-checkbox" :id id :name "shown" :value k
                  :checked (contains? visible-keys (:key column))}])
       ;; Always shown: checked, disabled, and with no name so it isn't sent.
       [:input {:type "checkbox" :class "spl-checkbox" :id id :checked true :disabled true}])
     [:label {:for id} (:name column)]]))

(defn picker
  "A button and the popover it opens: one checkbox per column, then Apply and
  Reset. The popover is a native popover so the table's scroll containers
  can't clip it."
  [& {:keys [list-key columns visible-keys action]}]
  (let [panel-id (str "column-picker-" (name list-key))]
    (list
      [:button {:type "button"
                :class "spl-col-picker-btn"
                :popovertarget panel-id
                :aria-label (tr "Columns")}
       (heroicons/chevron-down)]
      [:div {:id panel-id
             :popover "auto"
             :class "spl-col-picker"
             :x-data "columnPicker"
             :x-on:toggle "place($event)"}
       [:form {:hx-post action
               :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
               :hx-swap "none"}
        [:p {:class "spl-col-picker-title"} (tr "Columns")]
        [:ul {:class "spl-col-picker-list"}
         (for [column columns]
           (option column visible-keys))]
        [:div {:class "spl-col-picker-foot"}
         [:button {:type "submit" :class "spl-btn spl-btn--primary spl-btn--sm"}
          (tr "Apply")]
         [:button {:type "submit" :name "reset" :value "1"
                   :class "spl-btn spl-btn--ghost spl-btn--sm"}
          (tr "Reset to defaults")]]]])))
