(ns sepal.app.ui.export
  "Export modal component for CSV downloads."
  (:require [clojure.string :as str]
            [sepal.app.ui.icons.lucide :as lucide]))

(defn export-button
  "Button that opens the export modal."
  []
  [:button {:type "button"
            :class "btn btn-sm btn-ghost gap-1"
            :onclick "export_modal.showModal()"}
   (lucide/download :class "size-4")
   [:span "Export"]])

(defn export-modal
  "Export modal with optional data checkboxes.
   
   Uses GET form for conceptual correctness (export is a read operation).
   
   Checkbox handling: HTML checkboxes only submit when checked, but we need
   to send 'false' when unchecked since our defaults are true. We use Alpine
   to sync hidden inputs with checkbox state - hidden inputs always submit,
   so the server receives explicit true/false values.
   
   Arguments:
     :total         - Total number of results to export
     :search-query  - Current search query string (carried through to export)
     :export-action - URL for the export endpoint
     :options       - Vector of {:id \"param_name\" :label \"Display label\"} maps
                      for optional data checkboxes (all default to checked)"
  [& {:keys [total search-query export-action options]}]
  (let [large-export? (> total 1000)
        ;; Build Alpine x-data object with all options defaulting to true
        alpine-data (if (seq options)
                      (str "{ "
                           (->> options
                                (map #(str (:id %) ": true"))
                                (str/join ", "))
                           " }")
                      "{}")]
    [:dialog#export_modal {:class "modal"}
     [:div {:class "modal-box"}
      [:h3 {:class "font-bold text-lg"} "Export to CSV"]

      [:form {:method "GET"
              :action export-action
              :x-data alpine-data}
       ;; Carry search query through to export
       [:input {:type "hidden" :name "q" :value (or search-query "")}]

       ;; Hidden inputs for checkbox values - always submitted
       ;; Alpine keeps these synced with checkbox state
       (for [{:keys [id]} options]
         [:input {:type "hidden" :name id :x-bind:value id :key id}])

       ;; Row count with warning
       [:div {:class "py-4"}
        [:p (format "Exporting %,d results" total)]
        (when large-export?
          [:div {:class "alert alert-warning mt-2"}
           (lucide/triangle-alert :class "size-4")
           [:span "Large export — this may take a moment"]])]

       ;; Optional data checkboxes (resource-specific)
       (when (seq options)
         [:div {:class "form-control gap-2"}
          (for [{:keys [id label]} options]
            [:label {:class "label cursor-pointer justify-start gap-3" :key id}
             [:input {:type "checkbox"
                      :x-model id
                      :class "checkbox checkbox-sm"}]
             [:span {:class "label-text"} label]])])

       ;; Actions
       [:div {:class "modal-action"}
        [:button {:type "button"
                  :class "btn"
                  :onclick "export_modal.close()"}
         "Cancel"]
        [:button {:type "submit"
                  :class "btn btn-primary"
                  :onclick "setTimeout(() => export_modal.close(), 100)"}
         (lucide/download :class "size-4")
         "Export"]]]]

     ;; Click outside to close
     [:form {:method "dialog" :class "modal-backdrop"}
      [:button "close"]]]))
