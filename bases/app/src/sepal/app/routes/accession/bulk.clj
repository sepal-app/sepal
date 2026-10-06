(ns sepal.app.routes.accession.bulk
  "Acting on many accessions at once from the accession list."
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.bulk-tags :as bulk-tags]
            [sepal.app.ui.bulk :as ui.bulk]
            [sepal.i18n.interface :refer [tr]]
            [zodiac.core :as z]))

(defn tags-add-handler [request] (bulk-tags/add-tag request :accession))
(defn tags-remove-form-handler [request] (bulk-tags/remove-form request :accession))
(defn tags-remove-handler [request] (bulk-tags/remove-tag request :accession))

(defn action-bar
  "The bar and dialogs the accession list shows to someone who can edit."
  []
  (list
    (ui.bulk/action-bar
      :actions (list (ui.bulk/action-button :label (tr "Add tag") :dialog-id "bulk-accession-tag-add")
                     (bulk-tags/remove-button :form-url (z/url-for accession.routes/bulk-tags-remove)
                                              :dialog-id "bulk-accession-tag-remove")))
    (bulk-tags/add-dialog :id "bulk-accession-tag-add"
                          :action (z/url-for accession.routes/bulk-tags))
    (bulk-tags/remove-dialog :id "bulk-accession-tag-remove"
                             :action (z/url-for accession.routes/bulk-tags-remove))))
