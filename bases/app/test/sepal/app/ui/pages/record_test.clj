(ns sepal.app.ui.pages.record-test
  "Principle 9: views compose from shared components, and a section supplies
   only its body.

   This is the regression that motivated the shell. The accession General tab
   had padding because its body happened to emit `.spl-form`, which carried
   some; the Media tab emitted different markup and had none. Two sections of
   one record, spaced differently, because each page was deciding its own
   frame."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.pages.record :as pages.record])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup] (Jsoup/parseBodyFragment (chassis/html hiccup)))

(deftest test-shell-supplies-the-frame
  (let [body (parse (pages.record/page :code "2024.0117"
                                       :name [:span "Quercus alba"]
                                       :tabs [:nav "tabs"]
                                       :body [:p "section body"]
                                       :footer [:div "actions"]))]
    (is (some? (.selectFirst body ".spl-record-page")))
    (is (some? (.selectFirst body ".spl-record-code")))
    (is (some? (.selectFirst body ".spl-record-body")))
    (is (= "section body" (.text (.selectFirst body ".spl-record-body"))))))

(deftest test-header-is-omitted-when-there-is-nothing-to-identify
  (let [body (parse (pages.record/page :body [:p "x"]))]
    (is (nil? (.selectFirst body ".spl-record")))
    (is (some? (.selectFirst body ".spl-record-body")))))

(def ^:private section-pages
  "Every page that renders one section of a record. Each must go through its
   resource's shell rather than assembling a frame of its own."
  {"routes/accession/detail/general.clj"  "accession.shared/page"
   "routes/accession/detail/collection.clj" "accession.shared/page"
   "routes/accession/detail/media.clj"    "accession.shared/page"
   "routes/taxon/detail/name.clj"         "taxon.shared/page"
   "routes/taxon/detail/media.clj"        "taxon.shared/page"
   "routes/material/detail/general.clj"   "material.shared/page"
   "routes/material/detail/media.clj"     "material.shared/page"})

(defn- source [rel]
  (slurp (io/file (str "bases/app/src/sepal/app/" rel))))

(deftest test-every-section-page-composes-through-its-shell
  (testing "a section renders its body through the shared shell"
    (doseq [[rel shell] section-pages]
      (is (str/includes? (source rel) shell)
          (format "%s does not compose through %s" rel shell)))))

(deftest test-no-section-page-renders-a-bare-tab-row
  (testing "calling shared/tabs directly means assembling a frame by hand,
            which is how two sections of one record came to be spaced
            differently"
    (doseq [[rel _] section-pages
            :let [src (source rel)]]
      (doseq [call ["accession.shared/tabs" "taxon.shared/tabs"
                    "material.shared/tabs"]]
        (is (not (str/includes? src call))
            (format "%s calls %s directly instead of composing" rel call))))))

(deftest test-no-section-page-supplies-its-own-padding
  (testing "the shell owns the body padding; a section that adds its own
            reintroduces the divergence"
    (doseq [[rel _] section-pages
            :let [src (source rel)]]
      (is (not (re-find #"flex flex-col gap-8" src))
          (format "%s wraps its body in its own spacing" rel)))))
