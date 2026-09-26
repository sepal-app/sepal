(ns sepal.app.routes.material.detail.shared
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.propagation.routes :as propagation.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.app.ui.actions :as ui.actions]
            [sepal.app.ui.pages.record :as pages.record]
            [sepal.app.ui.tabs :as ui.tabs]
            [sepal.app.ui.taxon-name :as taxon-name]
            [sepal.code-template.interface :as ct.i]
            [sepal.i18n.interface :refer [tr trc]]
            [zodiac.core :as z]))

(def general-tab ::general)
(def media-tab ::media)
(def observations-tab ::observations)
(def tags-tab ::tags)

(defn- tab-items [& {:keys [active material]}]
  [(ui.tabs/item (tr "General")
                 {:href (z/url-for material.routes/detail-general {:id (:material/id material)})
                  :active (= active general-tab)})
   (ui.tabs/item (tr "Media")
                 {:href (z/url-for material.routes/detail-media {:id (:material/id material)})
                  :active (= active media-tab)})
   (ui.tabs/item (tr "Observations")
                 {:href (z/url-for material.routes/detail-observations {:id (:material/id material)})
                  :active (= active observations-tab)})
   (ui.tabs/item (tr "Tags")
                 {:href (z/url-for material.routes/detail-tags {:id (:material/id material)})
                  :active (= active tags-tab)})])

(defn tabs [material active]
  (ui.tabs/tabs {:label (tr "Material sections")
                 :items (tab-items :material material :active active)}))

(defn page
  "A material's record page. Its identifier is the accession code and the
  material code together, which is how a curator refers to it."
  [& {:keys [material accession taxon active body footer separator]}]
  (pages.record/page
    :wide? (= active media-tab)
    :code (when (and accession material)
            (ct.i/full-code separator (:accession/code accession) (:material/code material)))
    :name (when (:taxon/name taxon)
            (taxon-name/render (:taxon/name taxon) :author (:taxon/author taxon)))
    :tabs (tabs material active)
    :body body
    :footer footer))

(defn breadcrumbs [& {:keys [accession material taxon separator]}]
  [[:a {:href (z/url-for taxon.routes/index)}
    (tr "Taxa")]
   [:a {:href (z/url-for taxon.routes/detail-name {:id (:taxon/id taxon)})}
    (taxon-name/render (:taxon/name taxon))]
   [:a {:href (z/url-for accession.routes/index {} {:taxon-id (:taxon/id taxon)})}
    (tr "Accessions")]
   [:a {:href (z/url-for accession.routes/detail {:id (:accession/id accession)})}
    (:accession/code accession)]
   [:a {:href (z/url-for material.routes/index {} {:accession-id (:accession/id accession)})}
    (trc "navigation" "Material")]
   (ct.i/full-code separator (:accession/code accession) (:material/code material))])

(defn actions
  "The same actions on every one of a material's sections. Defined here rather
  than per section, which is how the sections diverged in the first place.

  :primary is the media section's Upload button. Nothing else varies."
  [& {:keys [material primary]}]
  (ui.actions/menu
    :primary primary
    :items [{:label (tr "Add a propagation")
             :href (z/url-for propagation.routes/new nil
                              {:parent-material-id (:material/id material)})}]
    :delete-url (z/url-for material.routes/delete {:id (:material/id material)})))
