(ns sepal.app.dev-user-ns-test
  "What development/src/user.clj may cost every JVM that loads it.

  Clojure auto-loads `user.clj` from the classpath before anything else, and
  `development/src` is on the classpath of every test run through the :dev
  alias. Requiring an application namespace there loaded the whole application
  before Kaocha started — 19 seconds a run, whether or not the tests being run
  had any use for it. Focusing a component test that needs no application went
  from 30 seconds to 9 once it stopped.

  Nothing enforced that but the reading of it, and the require that costs the 19
  seconds looks exactly like the one that does not."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ^:private user-clj "development/src/user.clj")

(defn- forms
  "Every form in user.clj."
  []
  (let [rdr (java.io.PushbackReader. (io/reader user-clj))]
    (with-open [rdr rdr]
      (doall (take-while #(not= ::eof %) (repeatedly #(read {:eof ::eof :read-cond :allow} rdr)))))))

(defn- required-namespaces []
  (->> (forms)
       (filter #(and (seq? %) (= 'ns (first %))))
       (mapcat (fn [ns-form] (filter #(and (seq? %) (= :require (first %))) ns-form)))
       (mapcat rest)
       (map #(if (sequential? %) (first %) %))
       (map str)))

(def ^:private forbidden
  "Requiring any of these loads the application. `sepal.app.instance` alone is
  9.3 seconds; `zodiac.ext.sql` reaches the same graph through the server."
  ["sepal.app." "zodiac."])

(deftest test-user-clj-requires-no-application-namespace
  (testing "the REPL entry points resolve what they need when called, so that
            loading user.clj costs a test run nothing"
    (doseq [required (required-namespaces)
            prefix forbidden]
      (is (not (str/starts-with? required prefix))
          (format "%s requires %s, which loads the application into every test JVM"
                  user-clj required)))))

(deftest test-every-deferred-symbol-resolves
  (testing "requiring-resolve fails when you call it, not when the file loads,
            so a symbol renamed out from under go or stop would surface as a
            broken REPL rather than a broken build"
    (let [src (slurp user-clj)
          symbols (->> (re-seq #"\(requiring-resolve '([\w.-]+/[\w.!?*<>=+-]+)\)" src)
                       (map second)
                       distinct)]
      (is (seq symbols)
          "no deferred symbols found — if the requires came back, the test above
           should have failed first")
      (doseq [s symbols]
        (is (requiring-resolve (symbol s))
            (format "%s defers to %s, which does not resolve" user-clj s))))))
