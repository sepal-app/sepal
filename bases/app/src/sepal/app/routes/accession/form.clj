(ns sepal.app.routes.accession.form
  (:require [clojure.string :as str]
            [sepal.accession.interface.spec :as accession.spec]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.page :as ui.page]
            [zodiac.core :as z]))

(defn enum-label-fn [v]
  (-> v
      (name)
      (str/replace "_" " ")
      (str/capitalize)))

(defn form [& {:keys [action errors values]}]
  (ui.page/page-inner
    (ui.form/form
      {:action action
       :method "POST"
       :id "accession-form"
       :x-on:accession-form:submit.window "$el.submit()"
       :x-on:accession-form:reset.window "$el.reset()"}
      [:div {:class "max-w-3xl"}
       (ui.form/anti-forgery-field)
       [:div {:class "max-w-xs"}
        (ui.form/input-field :label "Code"
                             :name "code"
                             :require true
                             :value (:code values)
                             :errors (:code errors))]

       (let [url (z/url-for taxon.routes/index)]
         (ui.form/field :label "Taxon"
                        :name "taxon-id"
                        :input [:select {:x-taxon-field (json/js {:url url})
                                         :id "taxon-id"
                                         :required true
                                         :name "taxon-id"
                                         :autocomplete "off"}
                                (when (:taxon-id values)
                                  [:option {:value (:taxon-id values)}
                                   (:taxon-name values)])]))
       [:div {:class "grid grid-cols-2"}
        [:div
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
                                                    :label-fn enum-label-fn))]
        [:div
         (ui.form/field :label "Provenance Type"
                        :name "provenance-type"
                        :input (ui.form/enum-select "provenance-type"
                                                    accession.spec/provenance-type
                                                    (:provenance-type values)
                                                    :label-fn enum-label-fn))

          ;; TODO: This should only be set when the provenance type is "wild"
         (ui.form/field :label "Wile Provenance Status"
                        :name "wild-provenance-status"
                        :input (ui.form/enum-select "wild-provenance-status"
                                                    accession.spec/wild-provenance-status
                                                    (:wild-provenance-status values)
                                                    :label-fn enum-label-fn))]]])

    [:script {:type "module"
              :src (html/static-url "app/routes/accession/form.ts")}]))
