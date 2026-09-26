(ns sepal.malli.interface-test
  (:require [clojure.test :as test :refer :all]
            [malli.core :as m]
            [sepal.i18n.interface :as i18n]
            [sepal.malli.interface :as malli.i]
            [sepal.store.interface :as store.i]))

(use-fixtures :once (fn [f] (malli.i/init) (f)))

(deftest json-schema-test
  (testing "json decode/store"
    (is (= (store.i/coerce :json "{}") {}))
    (is (= (store.i/coerce :json "{\"x\": 1}") {"x" 1}))
    (is (= (store.i/coerce :json "[{\"x\": 1}]") [{"x" 1}])))
  (testing "json encode/store"
    (is (= (store.i/encode :json {"x" 1}) "{\"x\":1}"))
    (is (= (store.i/encode :json [{"x" 1}]) "[{\"x\":1}]"))))

(defn- humanize [schema value]
  (malli.i/humanize (m/explain schema value)))

(deftest test-humanize-in-english
  (is (= {:name ["missing required key"]} (humanize [:map [:name :string]] {})))
  (is (= {:name ["should be at least 8 characters"]}
         (humanize [:map [:name [:string {:min 8}]]] {:name "abc"})))
  (is (= {:name ["should be at least 1 character"]}
         (humanize [:map [:name [:string {:min 1}]]] {:name ""})))
  (is (= ["should be a string"] (humanize :string 1)))
  (is (= ["should be at least 3"] (humanize [:>= 3] 1)))
  (is (= ["should be one of: admin, editor"] (humanize [:enum :admin :editor] :x)))
  (is (= ["Passwords do not match"]
         (humanize [:fn {:error/message "Passwords do not match"} (constantly false)] 1))
      "a schema's own message passes through"))

(deftest test-humanize-translates
  (i18n/load-catalogs!
    {"es" (i18n/parse-catalog "es" "msgid \"\"
msgstr \"\"
\"Plural-Forms: nplurals=2; plural=(n != 1);\\n\"

msgid \"missing required key\"
msgstr \"falta un campo obligatorio\"

msgid \"should be at least %1 character\"
msgid_plural \"should be at least %1 characters\"
msgstr[0] \"debe tener al menos %1 carácter\"
msgstr[1] \"debe tener al menos %1 caracteres\"

msgid \"Passwords do not match\"
msgstr \"Las contraseñas no coinciden\"
")})
  (try
    (i18n/with-locale "es"
      (is (= {:name ["falta un campo obligatorio"]} (humanize [:map [:name :string]] {})))
      (is (= {:name ["debe tener al menos 8 caracteres"]}
             (humanize [:map [:name [:string {:min 8}]]] {:name "abc"})))
      (is (= ["Las contraseñas no coinciden"]
             (humanize [:fn {:error/message "Passwords do not match"} (constantly false)] 1))))
    (finally
      (i18n/load-catalogs! {}))))
