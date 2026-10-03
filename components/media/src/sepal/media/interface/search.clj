(ns sepal.media.interface.search
  "Search field definitions for media."
  (:require [clojure.string :as str]
            [sepal.i18n.interface :refer [N_]]
            [sepal.search.interface :as search.i]))

(defn- linked-to
  "Media linked to a record of `resource-type`, or to nothing for \"none\"."
  [resource-type]
  (let [link {:select [1]
              :from [[:media_link :ml]]
              :where [:= :ml.media_id :md.id]}]
    (if (= "none" resource-type)
      [:not [:exists link]]
      [:exists (update link :where #(vector :and % [:= :ml.resource_type resource-type]))])))

(defn- linked-filter [{:keys [value values]}]
  (let [clauses (keep #(some-> % str/lower-case linked-to) (or values [value]))]
    (if (next clauses)
      (into [:or] clauses)
      (first clauses))))

(defmethod search.i/search-config :media [_]
  {:table [:media :md]
   :fields
   {;; Both read media_fts, and MATCH isn't limited to one column: plain text
    ;; and either field also match the linked record's label.
    :title       {:column :md.title
                  :type :fts
                  :fts-table :media_fts
                  :label (N_ "Title")}

    :description {:column :md.description
                  :type :fts
                  :fts-table :media_fts
                  :label (N_ "Description")}

    :type        {:column :md.media_type
                  :type :text
                  :label (N_ "Type")}

    :uploaded    {:column :md.created_at
                  :type :timestamp
                  :label (N_ "Uploaded")}

    ;; A subquery rather than :joins, which are inner joins and so could
    ;; never find unlinked media.
    :linked      {:type :enum
                  :values [:none :accession :material :taxon :location]
                  :label (N_ "Linked")
                  :filter-clause linked-filter}

    :id          {:column :md.id
                  :type :id
                  :label (N_ "ID")}}})
