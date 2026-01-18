(ns sepal.app.routes.contact.panel
  "Resource panel content for contacts.
   Displays contact summary, statistics, linked resources, and activity."
  (:require [sepal.accession.interface :as acc.i]
            [sepal.activity.interface :as activity.i]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.resource-panel :as panel]
            [zodiac.core :as z]))

(defn panel-content
  "Render the contact panel content.

   Options:
   - :contact        - The contact map
   - :stats          - Map with :accession-count
   - :activities     - Recent activities for this contact
   - :activity-count - Total activity count
   - :timezone       - Timezone string for formatting timestamps
   - :on-close       - Optional close handler (for list page)"
  [& {:keys [contact stats activities activity-count timezone on-close]}]
  (let [{:contact/keys [id name email phone business]} contact
        {:keys [accession-count]} stats]
    (panel/panel-container
      :children
      (list
        ;; Header
        (panel/panel-header
          :title name
          :subtitle business
          :on-close on-close)

        ;; Summary section
        (panel/collapsible-section
          :title "Summary"
          :children
          (panel/summary-section
            :fields [{:label "Name" :value name}
                     {:label "Business" :value business}
                     {:label "Email" :value email}
                     {:label "Phone" :value phone}]))

        ;; Statistics section
        (panel/collapsible-section
          :title "Statistics"
          :count accession-count
          :disabled? (zero? (or accession-count 0))
          :empty-label "none"
          :children
          (panel/statistics-section
            :stats [{:label "Accessions supplied"
                     :value accession-count
                     :href (z/url-for accession.routes/index nil {:supplier-contact-id id})}]))

        ;; Activity section
        (panel/collapsible-section
          :title "Activity"
          :count activity-count
          :disabled? (zero? (or activity-count 0))
          :empty-label "none"
          :default-open? false
          :children
          (panel/activity-section
            :activities activities
            :total-count activity-count
            :timezone timezone))))))

(defn fetch-panel-data
  "Fetch all data needed for the contact panel.
   Returns a map with :contact, :stats, :activities, :activity-count."
  [db contact]
  (let [contact-id (:contact/id contact)
        accession-count (acc.i/count-by-supplier-contact-id db contact-id)
        activities (activity.i/get-by-resource db
                                               :resource-type :contact
                                               :resource-id contact-id
                                               :limit 5)
        activity-count (activity.i/count-by-resource db
                                                     :resource-type :contact
                                                     :resource-id contact-id)]
    {:contact contact
     :stats {:accession-count accession-count}
     :activities activities
     :activity-count activity-count}))

(defn handler
  "Handler for contact panel route. Returns HTML fragment for HTMX."
  [{:keys [::z/context]}]
  (let [{:keys [db resource timezone]} context
        panel-data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :contact (:contact panel-data)
        :stats (:stats panel-data)
        :activities (:activities panel-data)
        :activity-count (:activity-count panel-data)
        :timezone timezone))))
