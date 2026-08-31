(ns sepal.app.ui.markup-test
  "Markup mistakes Chassis accepts in silence.

   Chassis renders an unknown keyword as a tag. There is no error and no
   warning — the wrong thing simply appears in the page."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- clj-sources []
  (->> (file-seq (io/file "bases/app/src"))
       (filter #(.isFile ^java.io.File %))
       (filter #(str/ends-with? (.getName ^java.io.File %) ".clj"))))

(deftest test-no-fragment-element
  (testing "Chassis has no fragment: `[:<> a b]` renders a literal
            <<>>a b</<>> into the page. A seq splices into its parent, which
            is what the fragment was reached for. This has shipped twice — the
            panel's key-value list and the media link form."
    (doseq [f (clj-sources)
            [n line] (map-indexed (fn [i l] [(inc i) l])
                                  (str/split-lines (slurp f)))
            ;; Comments explaining the trap are not the trap. Two of the three
            ;; occurrences in the tree are prose about why not to do this.
            :let [line (str/replace line #";;.*$" "")]]
      (is (not (re-find #"\[:<>[\s\]]" line))
          (format "%s:%d uses [:<>], which renders a literal <<>> tag"
                  (.getPath ^java.io.File f) n)))))
