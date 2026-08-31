(ns sepal.app.routes.accession.form
  (:require [clojure.string :as str]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.contact.routes :as contact.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as ui.form]
            [zodiac.core :as z]))

(defn enum-label-fn [v]
  (-> v
      (name)
      (str/replace "_" " ")
      (str/capitalize)))

(defn form [& {:keys [action errors supplier taxon values]}]
  [:div
   (ui.form/form
     {:id "accession-form"
      :hx-post action
      :hx-swap "none"
      :x-on:accession-form:submit.window "$el.requestSubmit()"
      :x-on:accession-form:reset.window "$el.reset()"}
     [:div {:class "spl-form"}
      (ui.form/anti-forgery-field)
      (ui.form/section
        :title "Identity"
        :hint "What this accession is, and what you call it."
        :children
        [(ui.form/input-field :label "Code"
                              :name "code"
                              :required true
                              :minlength 1
                              :value (:code values)
                              :errors (:code errors)
                              :help "Your garden's accession number. Must be unique.")

         (let [taxa-url (z/url-for taxon.routes/index)]
           (ui.form/field :label "Taxon"
                          :name "taxon-id"
                          :errors (:taxon-id errors)
                          :input [:select {:x-taxon-field (json/js {:url taxa-url})
                                           :id "taxon-id"
                                           :required true
                                           :name "taxon-id"
                                           :autocomplete "off"}
                                  (when (:taxon/id taxon)
                                    [:option {:value (:taxon/id taxon)}
                                     (:taxon/name taxon)])]
                          :required true
                          :help "Start typing a name to search the taxonomy."))

         [:div {:class "spl-form-pair"}
          (ui.form/field :label "ID Qualifier"
                         :name "id-qualifier"
                         :input (ui.form/enum-select "id-qualifier"
                                                     accession.spec/id-qualifier
                                                     (:id-qualifier values)))
          ;; TODO: This should only be set when the id-qualifier is set
          (ui.form/field :label "ID Qualifier Rank"
                         :name "id-qualifier-rank"
                         :input (ui.form/enum-select "id-qualifier-rank"
                                                     accession.spec/id-qualifier-rank
                                                     (:id-qualifier-rank values)
                                                     :label-fn enum-label-fn))]])

      (ui.form/section
        :title "Provenance"
        :hint "Where the material came from. Wild status applies only to wild-collected material."
        :children
        [[:div {:class "spl-form-pair"}
          (ui.form/field :label "Provenance Type"
                         :name "provenance-type"
                         :input (ui.form/enum-select "provenance-type"
                                                     accession.spec/provenance-type
                                                     (:provenance-type values)
                                                     :label-fn enum-label-fn))

          ;; TODO: This should only be set when the provenance type is "wild"
          (ui.form/field :label "Wild Provenance Status"
                         :name "wild-provenance-status"
                         :input (ui.form/enum-select "wild-provenance-status"
                                                     accession.spec/wild-provenance-status
                                                     (:wild-provenance-status values)
                                                     :label-fn enum-label-fn))]

         (ui.form/field :label "Supplier"
                        :name "supplier-contact-id"
                        :input [:select {:x-contact-field (json/js {:url (z/url-for contact.routes/index)})
                                         :id "supplier-contact-id"
                                         :name "supplier-contact-id"
                                         :autocomplete "off"}
                                [:option {:value "" :data-placeholder "true"} ""]
                                (when (:contact/id supplier)
                                  [:option {:value (:contact/id supplier)}
                                   (:contact/name supplier)])])])

      (ui.form/section
        :title "Dates"
        :children
        [[:div {:class "spl-form-pair"}
          (ui.form/input-field :label "Date Received"
                               :name "date-received"
                               :type "date"
                               :value (:date-received values)
                               :errors (:date-received errors))
          (ui.form/input-field :label "Date Accessioned"
                               :name "date-accessioned"
                               :type "date"
                               :value (:date-accessioned values)
                               :errors (:date-accessioned errors))]])])
   [:script {:type "module"
             :src (html/static-url "app/routes/accession/form.ts")}]])
