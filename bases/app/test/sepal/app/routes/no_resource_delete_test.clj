(ns sepal.app.routes.no-resource-delete-test
  (:require [clojure.test :refer [deftest is testing]])
  (:import [java.io File]))

(deftest test-nothing-can-orphan-a-note
  (testing "no route deletes an accession, material or taxon"
    ;; A note's resource_id is polymorphic and carries no foreign key, so a
    ;; deleted parent would strand its notes. Sepal has no delete path for
    ;; these three resources, so it cannot happen. 049 owns the day that
    ;; changes; this test fails when it does, which is the point.
    (let [deleting (->> (file-seq (File. "bases/app/src/sepal/app/routes"))
                        (filter #(.isFile ^File %))
                        (filter #(re-find #"\.clj$" (.getName ^File %)))
                        (filter #(re-find #":delete" (slurp %)))
                        (mapv #(.getPath ^File %))
                        sort)]
      ;; The three tag entries delete a tag or one tag_link row, never the
      ;; tagged record itself, so none of them can orphan a note.
      (is (= ["bases/app/src/sepal/app/routes/accession/core.clj"
              "bases/app/src/sepal/app/routes/accession/detail/notes.clj"
              "bases/app/src/sepal/app/routes/material/core.clj"
              "bases/app/src/sepal/app/routes/material/detail/notes.clj"
              "bases/app/src/sepal/app/routes/media/core.clj"
              "bases/app/src/sepal/app/routes/media/detail.clj"
              "bases/app/src/sepal/app/routes/media/detail/link.clj"
              "bases/app/src/sepal/app/routes/tag/detail.clj"
              "bases/app/src/sepal/app/routes/taxon/core.clj"
              "bases/app/src/sepal/app/routes/taxon/detail/notes.clj"]
             deleting)
          (str "A new delete route appeared. If it deletes an accession, "
               "material or taxon, its notes have to be deleted with it, or "
               "they outlive the record and leak into the next one to reuse "
               "the id.")))))
