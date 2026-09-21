(ns sepal.app.routes.propagation.panel
  "The propagation record, as the list's slide-in and as the detail page.

  The record reads as history -- started and succeeded with their dates -- and
  the products read as what exists now, listed separately. They must not sit
  side by side inviting subtraction: 12 struck and 6 kept are both true, about
  different moments."
  (:require [sepal.accession.interface :as accession.i]
            [sepal.app.authorization :as authz]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.propagation.shared :as shared]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.resource-panel :as panel]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.propagation.interface.permission :as propagation.perm]
            [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(defn- parent-href
  "The named plant is the more precise parent, so it is the link's target when
  there is one."
  [parent parent-material]
  (if parent-material
    (z/url-for material.routes/detail {:id (:material/id parent-material)})
    (z/url-for accession.routes/detail {:id (:accession/id parent)})))

(defn- close-out
  "Two ways to take a batch off the nursery list. A propagator closes a batch
  out in one click rather than waiting for someone to create records."
  [propagation editable?]
  (when (and editable? (= :active (:propagation/status propagation)))
    (let [id (:propagation/id propagation)]
      [:form {:class "flex gap-2 px-4 py-3"
              :hx-post (z/url-for propagation.routes/detail {:id id})
              :hx-swap "none"}
       (ui.form/anti-forgery-field)
       [:button {:type "submit"
                 :name "status"
                 :value "complete"
                 :class "spl-btn spl-btn--primary"}
        "Mark complete"]
       [:button {:type "submit"
                 :name "status"
                 :value "failed"
                 :class "spl-btn"}
        "Mark failed"]])))

(defn panel-content
  "Options:

  - :propagation         the row
  - :parent              its parent accession
  - :parent-material     the individual plant, when one is named
  - :location            where the batch sits, when one does
  - :rootstock           the graft's rootstock taxon, when one is set
  - :type-label          the method's label
  - :status-label        the status's label
  - :material-products   material the propagation produced
  - :accession-products  accessions the propagation produced
  - :editable?           whether to offer the close-out actions
  - :on-close            the list panel's close handler"
  [& {:keys [propagation parent parent-material location rootstock
             type-label status-label material-products accession-products
             editable? on-close]}]
  (let [{:keys [propagated-on succeeded-on quantity-started quantity-succeeded]} propagation
        parent-name (shared/parent-name parent parent-material)
        products (shared/product-links (:accession/code parent)
                                       material-products
                                       accession-products)]
    (panel/panel-container
      :children
      (list
        (panel/panel-header
          :title parent-name
          :subtitle (str type-label " · " status-label)
          :on-close on-close
          :actions (close-out propagation editable?))

        (panel/collapsible-section
          :title "Summary"
          :children
          (panel/summary-section
            :fields [{:label "Method" :value type-label}
                     {:label "Status" :value status-label}
                     {:label "Parent"
                      :value [:a {:href (parent-href parent parent-material)
                                  :class "spl-link"}
                              parent-name]}
                     {:label "Location"
                      :value (when location
                               [:a {:href (z/url-for location.routes/detail
                                                     {:id (:location/id location)})
                                    :class "spl-link"}
                                (:location/name location)])}
                     {:label "Rootstock" :value (:taxon/name rootstock)}]))

        (panel/collapsible-section
          :title "History"
          :children
          (panel/summary-section
            :fields [{:label "Propagated" :value propagated-on}
                     {:label "Started" :value (some-> quantity-started str)}
                     {:label "Succeeded on" :value succeeded-on}
                     {:label "Succeeded" :value (some-> quantity-succeeded str)}]))

        (panel/collapsible-section
          :title "Products"
          :count (count products)
          :disabled? (empty? products)
          :empty-label "nothing recorded"
          :children
          (panel/linked-resources-section :links products))))))

(defn fetch-panel-data
  "Everything the panel renders, fetched once.

  Returns the data keys `panel-content` takes, minus :editable? and :on-close,
  which are about the viewer and the surface rather than the record."
  [db propagation]
  (let [parent (accession.i/get-by-id db (:propagation/parent-accession-id propagation))
        parent-material (some->> (:propagation/parent-material-id propagation)
                                 (material.i/get-by-id db))
        location (some->> (:propagation/location-id propagation)
                          (location.i/get-by-id db))
        rootstock (some->> (:propagation/rootstock-taxon-id propagation)
                           (taxon.i/get-by-id db))]
    {:propagation propagation
     :parent parent
     :parent-material parent-material
     :location location
     :rootstock rootstock
     :type-label (shared/label (shared/type-labels db) (:propagation/type propagation))
     :status-label (shared/label (shared/status-labels db) (:propagation/status propagation))
     :material-products (material.i/list-by-propagation-id
                          db (:propagation/id propagation))
     :accession-products (accession.i/list-by-propagation-id
                           db (:propagation/id propagation))}))

(defn handler
  "The list's slide-in: the same content the detail page shows."
  [{:keys [::z/context viewer]}]
  (let [{:keys [db resource]} context
        data (fetch-panel-data db resource)]
    (html/render-partial
      (panel-content
        :propagation (:propagation data)
        :parent (:parent data)
        :parent-material (:parent-material data)
        :location (:location data)
        :rootstock (:rootstock data)
        :type-label (:type-label data)
        :status-label (:status-label data)
        :material-products (:material-products data)
        :accession-products (:accession-products data)
        :editable? (authz/user-has-permission? viewer propagation.perm/edit)))))
