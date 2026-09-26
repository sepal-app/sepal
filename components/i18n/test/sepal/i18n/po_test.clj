(ns sepal.i18n.po-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [sepal.i18n.po :as po]))

(defn- fixture []
  (po/parse (slurp (io/resource "sepal/i18n/fixture.po"))))

(defn- by-msgid [entries msgid]
  (first (filter #(= msgid (:msgid %)) entries)))

(deftest test-header
  (let [header (first (fixture))]
    (is (= "" (:msgid header)))
    (is (= {"Content-Type" "text/plain; charset=UTF-8"
            "Language" "es"
            "Plural-Forms" "nplurals=2; plural=(n != 1);"}
           (po/header-fields (:msgstr header))))))

(deftest test-simple-entry-keeps-its-comments
  (is (= {:msgid "Accessions"
          :msgstr "Accesiones"
          :extracted ["Heading on the accession list"]
          :references ["bases/app/src/sepal/app/ui/page.clj:22"
                       "bases/app/src/sepal/app/routes/accession/index.clj:10"]}
         (by-msgid (fixture) "Accessions"))))

(deftest test-msgctxt
  (is (= {:msgctxt "accession" :msgid "Code" :msgstr "Código"}
         (by-msgid (fixture) "Code"))))

(deftest test-continuation-lines-join
  (is (= "Un mensaje largo que ocupa varias líneas"
         (:msgstr (by-msgid (fixture) "A long message that spans lines")))))

(deftest test-escapes
  (is (= "Di \"hola\"\n\tluego \\ vete"
         (:msgstr (by-msgid (fixture) "Say \"hi\"\n\tthen \\ leave")))))

(deftest test-plural
  (is (= {:msgid "One accession"
          :msgid-plural "%1 accessions"
          :msgstr-plural ["Una accesión" "%1 accesiones"]}
         (by-msgid (fixture) "One accession"))))

(deftest test-flags-and-previous
  (let [e (by-msgid (fixture) "Accession code")]
    (is (= #{"fuzzy" "clojure-format"} (:flags e)))
    (is (= ["msgid \"Accession number\""] (:previous e)))))

(deftest test-untranslated
  (is (= "" (:msgstr (by-msgid (fixture) "Untranslated")))))

(deftest test-obsolete
  (is (= {:msgid "Gone" :msgstr "Ido" :obsolete? true}
         (by-msgid (fixture) "Gone"))))

(deftest test-entry-count
  (testing "the header, seven entries and one obsolete one"
    (is (= 9 (count (fixture))))))

(deftest test-malformed
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"line 2"
                        (po/parse "msgid \"a\"\nmsgstr \"unterminated\n"))))

(deftest test-write-round-trips
  (let [entries (fixture)]
    (is (= entries (po/parse (po/write entries))))))

(deftest test-write-splits-at-newlines
  (is (= "msgid \"\"\n\"one\\n\"\n\"two\"\nmsgstr \"\"\n"
         (po/write [{:msgid "one\ntwo" :msgstr ""}]))))
