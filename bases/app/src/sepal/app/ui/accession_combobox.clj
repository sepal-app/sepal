(ns sepal.app.ui.accession-combobox
  "The accession picker, for every form that chooses an accession.

  One definition so the pickers search the same endpoint and read the same
  way. The accession list answers its options, and a bare word there matches
  the code or the taxon name."
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.combobox :as combobox]
            [zodiac.core :as z]))

(defn accession-combobox
  "Options:

  - :name           the field the form submits
  - :label          what it is called, \"Accession\" unless given
  - :accession-id   the accession already chosen, if any
  - :accession-text the text shown for it
  - :required, :errors, :help, :label-hidden?  as `combobox/combobox` takes them"
  [& {:keys [name label accession-id accession-text required errors help label-hidden?]
      :or {label "Accession"}}]
  (combobox/combobox
    :name name
    :label label
    :url (z/url-for accession.routes/index)
    :required required
    :errors errors
    :help help
    :label-hidden? label-hidden?
    :selected (when accession-id
                {:id accession-id :text accession-text})))
