(ns sepal.i18n.interface-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [are deftest is testing]]
            [sepal.i18n.interface :as i18n]))

(def ^:private es
  (i18n/parse-catalog "es" (slurp (io/resource "sepal/i18n/fixture.po"))))

(def ^:private pl
  (i18n/parse-catalog "pl" "msgid \"\"
msgstr \"\"
\"Plural-Forms: nplurals=3; plural=(n==1 ? 0 : n%10>=2 && n%10<=4 && (n%100<10 || n%100>=20) ? 1 : 2);\\n\"

msgid \"One file\"
msgid_plural \"%1 files\"
msgstr[0] \"Jeden plik\"
msgstr[1] \"%1 pliki\"
msgstr[2] \"%1 plików\"
"))

(defmacro ^:private with-catalog [catalog & body]
  `(binding [i18n/*catalog* ~catalog
             i18n/*locale* (:locale ~catalog)]
     ~@body))

(deftest test-english-returns-the-msgid
  (is (= "Accessions" (i18n/tr "Accessions")))
  (is (= "Code" (i18n/trc "accession" "Code")))
  (is (= "Invitation sent to a@b.c" (i18n/tr "Invitation sent to %1" "a@b.c")))
  (is (= "a, b and c" (i18n/tr "%1, %2 and %3" "a" "b" "c")))
  (is (= "1, 2, 3 and 4" (i18n/tr "%1, %2, %3 and %4" 1 2 3 4))))

(deftest test-english-plurals
  (is (= "One accession" (i18n/trn "One accession" "%1 accessions" 1)))
  (is (= "3 accessions" (i18n/trn "One accession" "%1 accessions" 3)))
  (is (= "0 accessions" (i18n/trn "One accession" "%1 accessions" 0)))
  (testing "explicit arguments replace the count"
    (is (= "1,200 accessions" (i18n/trn "One accession" "%1 accessions" 1200 "1,200")))))

(deftest test-translated
  (with-catalog es
    (is (= "Accesiones" (i18n/tr "Accessions")))
    (is (= "Código" (i18n/trc "accession" "Code")))
    (is (= "Una accesión" (i18n/trn "One accession" "%1 accessions" 1)))
    (is (= "4 accesiones" (i18n/trn "One accession" "%1 accessions" 4)))))

(deftest test-context-is-not-interchangeable
  (with-catalog es
    (is (= "Code" (i18n/tr "Code")))
    (is (= "Code" (i18n/trc "postal" "Code")))))

(deftest test-miss-paths-return-the-msgid
  (with-catalog es
    (are [msgid] (= msgid (i18n/tr msgid))
      "Not in the catalog"
      "Untranslated"                    ; empty msgstr
      "Accession code"                  ; fuzzy
      "Gone"                            ; obsolete
      "")
    (is (= "Missing %1" (i18n/tr "Missing %1")))
    (is (= "Missing x" (i18n/tr "Missing %1" "x")))
    (is (= "2 things" (i18n/trn "One thing" "%1 things" 2)))))

(deftest test-missing-argument-renders-the-placeholder
  (is (= "a and %2" (i18n/tr "%1 and %2" "a"))))

(deftest test-three-plural-forms
  (with-catalog pl
    (are [n s] (= s (i18n/trn "One file" "%1 files" n))
      1 "Jeden plik"
      3 "3 pliki"
      5 "5 plików"
      22 "22 pliki")))

(deftest test-n-marks-without-translating
  (is (= "Accessions" (i18n/N_ "Accessions")))
  (with-catalog es
    (is (= "Accessions" (i18n/N_ "Accessions")))
    (is (= "Accesiones" (i18n/tr (i18n/N_ "Accessions"))))))

(deftest test-resolve-locale
  (let [available #{"es" "pt_BR"}]
    (are [header locale] (= locale (i18n/resolve-locale header available))
      nil nil
      "" nil
      "es" "es"
      "es-MX" "es"
      "ES-mx" "es"
      "fr" nil
      "fr-CA, fr;q=0.9, es;q=0.8, en;q=0.5" "es"
      "en-US, es;q=0.9" nil                 ; English is preferred
      "es;q=0.5, en;q=0.9" nil              ; q order, not header order
      "es;q=0.9, pt-BR;q=0.9" "es"          ; ties keep header order
      "es;q=0, fr" nil                      ; q=0 means not acceptable
      "*" nil
      "pt-BR" "pt_BR"
      "pt" nil                              ; no bare pt catalog
      "garbage;;q=x, es" "es"
      ";;;" nil)))

(deftest test-with-locale
  (testing "a locale with no catalog is English throughout"
    (i18n/with-locale "xx"
      (is (nil? i18n/*locale*))
      (is (nil? i18n/*catalog*))
      (is (= "Accessions" (i18n/tr "Accessions")))))
  (i18n/with-locale nil
    (is (nil? i18n/*locale*))))
