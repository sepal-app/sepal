(ns sepal.app.list-columns-metadata-test
  "The column definitions of every list with a picker. Each list's own tests
  compile its sorts; these check the keys the picker and the sort rely on."
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.list-columns :as list-columns]
            [sepal.app.routes.accession.index :as accession.index]
            [sepal.app.routes.contact.index :as contact.index]
            [sepal.app.routes.location.index :as location.index]
            [sepal.app.routes.material.index :as material.index]
            [sepal.app.routes.observation.index :as observation.index]
            [sepal.app.routes.propagation.index :as propagation.index]
            [sepal.app.routes.tag.index :as tag.index]
            [sepal.app.routes.taxon.index :as taxon.index]))

(def ^:private timezone "UTC")

(defn- list-columns
  "Each list's columns, built with placeholder arguments. Only the data keys
  are read, so no cell is rendered."
  []
  {:accession (accession.index/table-columns timezone)
   :contact (contact.index/table-columns timezone)
   :location (location.index/table-columns timezone)
   :material (material.index/table-columns :separator "." :timezone timezone)
   :observation (observation.index/table-columns nil "." timezone)
   :propagation (propagation.index/table-columns :type-labels {}
                                                 :status-labels {}
                                                 :separator "."
                                                 :timezone timezone)
   :tag (#'tag.index/table-columns timezone)
   :taxon (taxon.index/table-columns timezone)})

(deftest test-every-list-is-covered
  (is (= list-columns/lists (set (keys (list-columns))))))

(deftest test-column-metadata
  (doseq [[list-key columns] (list-columns)]
    (testing (name list-key)
      (is (seq columns))
      (doseq [column columns]
        (is (keyword? (:key column)) (str "a keyword :key on " (:name column)))
        (when (= 1 (:priority column))
          (is (not (:hidden? column))
              (str (:key column) " is priority 1, so it can't be hidden")))
        (when (contains? column :sort)
          (is (and (vector? (:sort column)) (seq (:sort column)))
              (str (:key column) " has a non-empty :sort vector"))))
      (let [ks (map :key columns)]
        (is (= (count ks) (count (set ks))) "keys are unique")))))
