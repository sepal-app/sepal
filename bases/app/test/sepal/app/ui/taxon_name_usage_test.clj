(ns sepal.app.ui.taxon-name-usage-test
  "Principle 2 says every scientific name renders through one function. Having
   the function is not the same as using it: it shipped wired into a single
   screen out of fourteen that display taxon names, so the serif never appeared
   anywhere except the accessions list.

   This checks the display sites, not the data ones — a name inside a JSON
   payload, a CSV export or a page title is a string, not markup."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ^:private display-namespaces
  "Files that render a taxon name into the page for a person to read."
  ["routes/accession/index.clj"
   "routes/accession/detail/shared.clj"
   "routes/taxon/index.clj"
   "routes/taxon/detail/shared.clj"
   "routes/material/index.clj"
   "routes/material/detail/shared.clj"])

(defn- source [rel]
  (slurp (io/file (str "bases/app/src/sepal/app/" rel))))

(deftest test-display-sites-use-the-renderer
  (testing "each screen that shows a taxon name calls ui.taxon-name/render"
    (doseq [rel display-namespaces]
      (is (str/includes? (source rel) "taxon-name/render")
          (format "%s renders a taxon name without the shared renderer" rel)))))

(deftest test-no-blanket-italic-on-a-taxon-name
  (testing "a Tailwind `italic` utility italicises the whole string, including
            the hybrid marker and the connecting terms that must stay upright.
            Only ui.taxon-name/render knows which fragments are which."
    (doseq [rel display-namespaces
            :let [src (source rel)]]
      (doseq [line (str/split-lines src)]
        (when (re-find #"taxon/name" line)
          (is (not (re-find #"\"italic\"|\bitalic\b" line))
              (format "%s italicises a whole taxon name: %s"
                      rel (str/trim line))))))))
