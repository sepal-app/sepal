(ns sepal.app.routes.taxon.parentage-row
  "One more parent slot for the taxon form.

  A cross usually has two parents and the form offers two, but a nothogenus can
  name four — × Potinara is Brassavola × Cattleya × Laelia × Sophronitis — and
  that case is why parentage is a table rather than two columns. Growing the
  form by one slot per save would make the case the design exists for the most
  tedious one to enter.

  Server-rendered rather than cloned in the browser because every id a
  `<sepal-combobox>` renders comes from its `name`: two rows built from one
  template would share `parentage-parent-0-input`, and `<label for>` and the
  aria wiring would both point at the wrong field."
  (:require [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.taxon.form :as taxon.form]))

(def ^:private Params
  [:map [:index {:default 0} :int]])

(defn handler
  "The row, plus the Add button again with the next index.

  The button is replaced out of band because the server is the only thing that
  knows which index it just handed out; the alternative is the page counting
  rows in JavaScript and the two disagreeing after a failed request."
  [{:keys [query-params]}]
  (let [{:keys [index]} (params/decode Params query-params)]
    (html/render-partial
      (list
        (taxon.form/parentage-row :index index)
        (assoc-in (taxon.form/parentage-add-button :next-index (inc index))
                  [1 :hx-swap-oob] "true")))))
