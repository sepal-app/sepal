(ns sepal.app.ui.form-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.button :as button]
            [sepal.app.ui.form :as form])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup] (Jsoup/parseBodyFragment (chassis/html hiccup)))

(deftest test-input-has-a-programmatic-label
  (testing "a single control needs <label for>, not a fieldset and legend —
            a legend labels a group, and nothing referenced it anyway"
    (let [body (parse (form/input-field :name "code" :label "Code"))
          input (.selectFirst body "input")
          label (.selectFirst body "label")]
      (is (some? input))
      (is (some? label))
      (is (= (.attr input "id") (.attr label "for"))
          "the label points at the input it labels")
      (is (= "Code" (str/trim (.text label)))))))

(deftest test-help-text-is-referenced-by-the-input
  (let [body (parse (form/input-field :name "code" :label "Code"
                                      :help "Must be unique."))
        input (.selectFirst body "input")
        help (.selectFirst body ".spl-help")]
    (is (some? help))
    (is (str/includes? (.attr input "aria-describedby") (.attr help "id"))
        "help text is announced with the field, not orphaned beside it")))

(deftest test-errors-mark-the-input-invalid
  (let [body (parse (form/input-field :name "code" :label "Code"
                                      :errors ["must not be blank"]))
        input (.selectFirst body "input")]
    (is (= "true" (.attr input "aria-invalid")))
    (is (str/includes? (.attr input "aria-describedby") "code-errors")
        "the error list is announced with the field")))

(deftest test-no-errors-means-no-invalid-flag
  (let [body (parse (form/input-field :name "code" :label "Code"))
        input (.selectFirst body "input")]
    (is (not= "true" (.attr input "aria-invalid")))))

(deftest test-required-is-marked-both-ways
  (let [body (parse (form/input-field :name "code" :label "Code" :required true))
        input (.selectFirst body "input")]
    (is (.hasAttr input "required") "the control is really required")
    (is (some? (.selectFirst body ".spl-required"))
        "and it is marked visually")))

(deftest test-section-renders-a-heading
  (let [body (parse (form/section :title "Identity"
                                  :hint "What this accession is."
                                  :children [:div "fields"]))]
    (is (= "Identity" (.text (.selectFirst body "h2, h3"))))
    (is (some? (.selectFirst body ".spl-form-section")))))

(deftest test-form-controls-emit-no-daisyui
  (let [html (str (chassis/html (form/input-field :name "a" :label "A"))
                  (chassis/html (form/textarea-field :name "b" :label "B"))
                  (chassis/html (form/enum-select "c" [:enum :x :y] nil)))]
    (doseq [cls ["input validator" "select-bordered" "textarea-bordered"
                 "fieldset" "validator-hint" "text-error"]]
      (is (not (str/includes? html cls))
          (str "form still emits " cls)))))

(deftest test-buttons-are-not-indigo
  (testing "ui/button.clj hardcoded bg-indigo-600 in an emerald-themed app"
    (let [html (str (chassis/html (button/button :text "Save"))
                    (chassis/html (button/link :text "Go" :href "/x")))]
      (doseq [cls ["indigo" "bg-blue" "text-white"]]
        (is (not (str/includes? html cls))
            (str "button still emits " cls)))
      (is (str/includes? html "spl-btn")))))
