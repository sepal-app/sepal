(ns sepal.app.kwarg-test
  "Keyword arguments a function never destructures evaporate silently.

   This class of bug has shipped four times on this branch alone:
   `:panel` for `:panel-content` twice, `:page` for `:page-num` twice, plus
   `:require` for `:required` on the login form. Clojure will not warn, tests
   that assert on selectors will not notice, and the symptom is a missing panel
   or a list that stops loading — never an error.

   These tests call the real functions with the real argument names."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.pages.detail :as pages.detail]))

(deftest test-detail-layout-ignores-unknown-keys
  (testing "documents the trap: an unknown key is accepted and dropped"
    (let [html (chassis/html (pages.detail/page-content-with-panel
                               :content [:p "c"]
                               :panel (list [:p {:id "leaked"} "panel"])))]
      (is (not (str/includes? html "leaked"))
          "`:panel` is not read — this is why the bug is silent"))))

(def ^:private list-routes
  ["routes/accession/index.clj"
   "routes/taxon/index.clj"
   "routes/material/index.clj"
   "routes/location/index.clj"
   "routes/contact/index.clj"])

(defn- source [rel]
  (slurp (io/file (str "bases/app/src/sepal/app/" rel))))

(defn- destructured-keys
  "The :keys vector of the first `(defn <name> [& {:keys [...]}]` in `src`."
  [src fn-name]
  (when-let [m (re-find (re-pattern (str "\\(defn " fn-name "\\s*(?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*)?\\[& \\{:keys \\[([^\\]]*)\\]"))
                        src)]
    (set (str/split (str/trim (second m)) #"\s+"))))

(defn- call-keys
  "The keywords passed directly at the first `(<name> …)` call site.

   Depth-aware: a regex over the call's text also captures keywords inside
   nested forms like `(uri/uri-str {:path uri :query …})`, which are arguments
   to something else entirely."
  [src fn-name]
  (when-let [start (str/index-of src (str "(" fn-name " :"))]
    (loop [i start, depth 0, brackets 0, acc #{}]
      (if (>= i (count src))
        acc
        (let [c (nth src i)]
          (cond
            (= c \() (recur (inc i) (inc depth) brackets acc)
            (= c \)) (if (= depth 1)
                       acc
                       (recur (inc i) (dec depth) brackets acc))
            (contains? #{\{ \[} c) (recur (inc i) depth (inc brackets) acc)
            (contains? #{\} \]} c) (recur (inc i) depth (dec brackets) acc)
            ;; A keyword argument of THIS call: directly inside its parens,
            ;; not nested in a map or vector literal.
            (and (= c \:) (= depth 1) (zero? brackets))
            (let [m (re-find #"^:([a-z][a-z0-9-]*)" (subs src i (min (count src) (+ i 40))))]
              (recur (inc i) depth brackets (if m (conj acc (second m)) acc)))
            :else (recur (inc i) depth brackets acc)))))))

(deftest test-index-rows-called-with-the-names-it-destructures
  (testing "regression: location and contact destructure `page-num` and were
            called with `:page`, so next-page-url got nil and infinite scroll
            stopped after one fetch"
    (doseq [rel list-routes
            :let [src (source rel)
                  declared (destructured-keys src "index-rows")
                  passed (call-keys src "index-rows")]
            :when (and declared passed)]
      (let [unknown (remove declared passed)]
        (is (empty? unknown)
            (format "%s calls index-rows with %s, which it does not destructure (it takes %s)"
                    rel (vec unknown) (vec (sort declared))))))))

(deftest test-table-called-with-the-names-it-destructures
  (doseq [rel list-routes
          :let [src (source rel)
                declared (destructured-keys src "table")
                passed (call-keys src "table")]
          :when (and declared passed)]
    (let [unknown (remove declared passed)]
      (is (empty? unknown)
          (format "%s calls table with %s, which it does not destructure (it takes %s)"
                  rel (vec unknown) (vec (sort declared)))))))

(deftest test-panel-content-receives-its-data
  (testing "regression: location/detail.clj closed the paren early, calling
            panel-content with no arguments and leaking its four arguments to
            the layout, which dropped them"
    (doseq [rel ["routes/location/detail.clj"
                 "routes/contact/detail.clj"
                 "routes/accession/detail/general.clj"
                 "routes/taxon/detail/name.clj"
                 "routes/material/detail/general.clj"]
            :let [src (source rel)]]
      (is (not (re-find #"panel-content\)\s*\n" src))
          (format "%s calls panel-content with no arguments" rel)))))
