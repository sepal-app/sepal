(ns sepal.app.ui.taxon-name-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.taxon-name :as taxon-name])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup]
  (Jsoup/parseBodyFragment (chassis/html hiccup)))

(deftest test-scientific-part-is-italic
  (let [body (parse (taxon-name/render "Quercus alba"))
        el (.selectFirst body "i.spl-sci")]
    (is (some? el) "the italicised part is an <i> carrying spl-sci")
    (is (= "Quercus alba" (.text el)))))

(deftest test-hybrid-marker-is-outside-the-italic-element
  (testing "× stays upright, so it must not sit inside an <i>"
    (let [body (parse (taxon-name/render "Nepenthes × hookeriana"))]
      (is (= 2 (.size (.select body "i.spl-sci")))
          "genus and epithet are two separate italic runs")
      (doseq [el (.select body "i.spl-sci")]
        (is (not (str/includes? (.text el) "×"))
            "the hybrid marker leaked into an italic run")))))

(deftest test-connecting-term-is-outside-the-italic-element
  (let [body (parse (taxon-name/render "Cyperus pangorei var. ambiguus"))]
    (is (= 2 (.size (.select body "i.spl-sci"))))
    (doseq [el (.select body "i.spl-sci")]
      (is (not (str/includes? (.text el) "var."))))))

(deftest test-cultivar-epithet-is-upright
  (let [body (parse (taxon-name/render "Acer palmatum 'Sango-kaku'"))]
    (is (= "Acer palmatum" (.text (.selectFirst body "i.spl-sci")))
        "only the botanical part is italic")
    (is (str/includes? (.text body) "'Sango-kaku'")
        "the epithet is still rendered")
    (doseq [el (.select body "i.spl-sci")]
      (is (not (str/includes? (.text el) "Sango"))
          "the epithet leaked into an italic run"))))

(deftest test-author-renders-upright-in-its-own-element
  (let [body (parse (taxon-name/render "Quercus alba" :author "L."))
        el (.selectFirst body ".spl-authority")]
    (is (some? el) "the authority gets its own class so it can be dimmed")
    (is (= "L." (str/trim (.text el))))
    (is (nil? (.selectFirst body "i.spl-authority"))
        "the authority is never italic")))

(deftest test-author-is-omitted-when-absent
  (is (nil? (.selectFirst (parse (taxon-name/render "Quercus alba"))
                          ".spl-authority")))
  (is (nil? (.selectFirst (parse (taxon-name/render "Quercus alba" :author ""))
                          ".spl-authority"))
      "an empty author string renders nothing rather than a stray space"))

(deftest test-blank-name-renders-nothing-visible
  (is (= "" (str/trim (.text (parse (taxon-name/render nil)))))))

(deftest test-full-text-is-preserved
  (testing "whatever the segmentation, the rendered text equals the input"
    (doseq [n ["Quercus alba"
               "Nepenthes × hookeriana"
               "Eleocharis mamillata subsp. austriaca"
               "Acer palmatum 'Sango-kaku'"
               "Camellia japonica 'Nuccio's Pearl'"
               "Hosta 'Sum and Substance'"]]
      (is (= n (str/trim (.text (parse (taxon-name/render n)))))
          (str "text changed when rendering " n)))))
