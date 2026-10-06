(ns sepal.app.ui.bulk
  "Markup for acting on the rows ticked in a list: the element holding the
  selection, the bar of actions, and one dialog per action."
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.json :as json]
            [sepal.app.ui.form :as ui.form]
            [sepal.i18n.interface :refer [tr trn]]))

(defn- count-text
  "An x-text expression: `one` or `other` with %1 replaced by the count."
  [one other]
  (format "(selected.length === 1 ? %s : %s).replace('%%1', selected.length)"
          (json/js one) (json/js other)))

(defn scope-attrs
  "On the element that holds the selection. A swap of the whole list (a new
  search, a sort, a bulk reload) clears it. Infinite scroll targets the
  sentinel, so appended rows keep it."
  [& {:keys [toolbar-id list-container-id]}]
  {:x-data (str "listSelection('" toolbar-id "')")
   :x-bind:class "{ 'spl-list-body--selecting': selected.length > 0 }"
   :x-on:htmx:before-request (str "if ($event.detail.target?.id === '" list-container-id "') clear()")
   :x-on:htmx:after-settle "countLoaded()"
   :x-on:bulk-applied "applied()"})

(defn action-bar [& {:keys [actions]}]
  [:div {:class "spl-toolbar spl-bulk-bar"
         :role "region"
         :aria-label (tr "Selected rows")
         :x-show "selected.length > 0"
         :x-cloak ""}
   [:p {:class "spl-bulk-count"
        :aria-live "polite"
        :x-text (count-text (trn "%1 selected" "%1 selected" 1 "%1")
                            (trn "%1 selected" "%1 selected" 2 "%1"))}]
   [:div {:class "spl-bulk-actions"} actions]
   [:button {:type "button"
             :class "spl-btn spl-btn--ghost spl-btn--sm"
             :x-on:click "clear()"}
    (tr "Clear")]])

(defn action-button [& {:keys [label dialog-id]}]
  [:button {:type "button"
            :class "spl-btn spl-btn--sm"
            :x-on:click (str "document.getElementById('" dialog-id "').showModal()")}
   label])

(defn ids-inputs []
  [:template {:x-for "id in selected" :x-bind:key "id"}
   [:input {:type "hidden" :name "ids" :x-bind:value "id"}]])

(defn dialog [& {:keys [id title action confirm-one confirm-other body]}]
  [:dialog {:id id :class "spl-modal"}
   [:div {:class "spl-modal-box"}
    [:h3 {:class "font-bold text-lg"} title]
    (ui.form/form {:hx-post action
                   :hx-swap "none"
                   :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})}
                  (ids-inputs)
                  [:div {:class "py-4"} body]
                  [:div {:class "spl-modal-actions"}
                   [:button {:type "button"
                             :class "spl-btn"
                             :x-on:click "$el.closest('dialog').close()"}
                    (tr "Cancel")]
                   [:button {:type "submit"
                             :class "spl-btn spl-btn--primary"
                             :x-bind:disabled "submitting"
                             :x-text (count-text confirm-one confirm-other)}]])]
   [:form {:method "dialog" :class "spl-modal-backdrop"}
    [:button (tr "Close")]]])
