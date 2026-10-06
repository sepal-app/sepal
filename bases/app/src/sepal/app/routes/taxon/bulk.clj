(ns sepal.app.routes.taxon.bulk
  "Acting on many taxa at once from the taxon list."
  (:require [sepal.app.routes.bulk-tags :as bulk-tags]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.bulk :as ui.bulk]
            [sepal.i18n.interface :refer [tr]]
            [zodiac.core :as z]))

(defn tags-add-handler [request] (bulk-tags/add-tag request :taxon))
(defn tags-remove-form-handler [request] (bulk-tags/remove-form request :taxon))
(defn tags-remove-handler [request] (bulk-tags/remove-tag request :taxon))

(defn action-bar
  "The bar and dialogs the taxon list shows to someone who can edit."
  []
  (list
    (ui.bulk/action-bar
      :actions (list (ui.bulk/action-button :label (tr "Add tag") :dialog-id "bulk-taxon-tag-add")
                     (bulk-tags/remove-button :form-url (z/url-for taxon.routes/bulk-tags-remove)
                                              :dialog-id "bulk-taxon-tag-remove")))
    (bulk-tags/add-dialog :id "bulk-taxon-tag-add"
                          :action (z/url-for taxon.routes/bulk-tags))
    (bulk-tags/remove-dialog :id "bulk-taxon-tag-remove"
                             :action (z/url-for taxon.routes/bulk-tags-remove))))
