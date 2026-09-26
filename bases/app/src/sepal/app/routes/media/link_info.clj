(ns sepal.app.routes.media.link-info
  "What a media link names and where it goes, for the link widget, the media
  panel and the link activity events."
  (:require [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.routes.location.routes :as location.routes]
            [sepal.app.routes.material.routes :as material.routes]
            [sepal.app.routes.taxon.routes :as taxon.routes]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(defn- text-query [resource-type resource-id separator]
  (case resource-type
    "accession" {:select [[[:concat :a.code " (" :t.name ")"] :text]]
                 :from [[:accession :a]]
                 :join [[:taxon :t] [:= :t.id :a.taxon-id]]
                 :where [:= :a.id resource-id]}
    "location" {:select [[[:concat :l.name " (" :l.code ")"] :text]]
                :from [[:location :l]]
                :where [:= :l.id resource-id]}
    "material" {:select [[[:concat :a.code (str separator) :m.code " (" :t.name ")"] :text]]
                :from [[:material :m]]
                :join [[:accession :a] [:= :a.id :m.accession_id]
                       [:taxon :t] [:= :t.id :a.taxon-id]]
                :where [:= :m.id resource-id]}
    ;; An expression, not the bare column: a column comes back qualified by
    ;; its table, as :taxon/text.
    "taxon" {:select [[[:concat :name] :text]]
             :from [:taxon]
             :where [:= :id resource-id]}
    nil))

(defn url
  "The record page a link points at, or nil for a type nothing here knows."
  [resource-type resource-id]
  (case resource-type
    "accession" (z/url-for accession.routes/detail {:id resource-id})
    "location" (z/url-for location.routes/detail {:id resource-id})
    "material" (z/url-for material.routes/detail {:id resource-id})
    "taxon" (z/url-for taxon.routes/detail {:id resource-id})
    nil))

(defn link-info
  "{:text :url :type} for a `media_link` row, or nil without one. An
  unrecognised resource type shows the type as its text and has no URL."
  [db link separator]
  (when link
    (let [{:media-link/keys [resource-type resource-id]} link
          query (text-query resource-type resource-id separator)]
      {:text (if query
               (:text (db.i/execute-one! db query))
               resource-type)
       :url (url resource-type resource-id)
       :type resource-type})))

(defn with-via
  "Mark each media item linked somewhere other than `resource-type`
  `resource-id` with :via, the {:text :url} of where it is linked."
  [db separator resource-type resource-id media]
  (mapv (fn [{:media/keys [link] :as item}]
          (if (and (= resource-type (:resource-type link))
                   (= resource-id (:resource-id link)))
            item
            (assoc item :via (link-info db
                                        {:media-link/resource-type (:resource-type link)
                                         :media-link/resource-id (:resource-id link)}
                                        separator))))
        media))
