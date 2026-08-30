(ns sepal.app.css.modal-test
  "Regression: `.spl-modal` sits on a native <dialog>. The UA stylesheet hides a
   closed dialog with `dialog:not([open]) { display: none }`, so giving
   `.spl-modal` a `display` value leaves every modal on screen from first paint
   — which is exactly what happened on the accessions list, where the export
   dialog covered the page and blocked every other control."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- components-css []
  (slurp (io/resource "sepal/app/css/components.css")))

(defn- rule-body
  "The declarations inside the first `selector { … }` block, or nil."
  [css selector]
  (when-let [i (str/index-of css (str selector " {"))]
    (let [start (str/index-of css "{" i)
          end (str/index-of css "}" start)]
      (subs css (inc start) end))))

(deftest test-modal-does-not-set-display
  (testing "any display on .spl-modal overrides the UA rule that hides it"
    (let [body (rule-body (components-css) ".spl-modal")]
      (is (some? body) "the .spl-modal rule exists")
      (is (not (str/includes? body "display:"))
          "`.spl-modal` must not set display — a closed <dialog> is hidden by
           the user agent, and setting display defeats that"))))

(deftest test-modal-does-not-set-position
  (testing "showModal() promotes the dialog to the top layer and centres it;
            position:fixed fights that and pins it to the viewport even closed"
    (let [body (rule-body (components-css) ".spl-modal")]
      (is (not (str/includes? body "position:"))))))

(deftest test-modal-has-a-backdrop-rule
  (is (str/includes? (components-css) ".spl-modal::backdrop")
      "the scrim comes from ::backdrop rather than a positioned element"))

(deftest test-dialog-backdrop-form-sits-behind-the-box
  (testing "the click-outside form must not cover the dialog's own controls"
    (let [body (rule-body (components-css) ".spl-modal-backdrop")]
      (is (some? body))
      (is (str/includes? body "z-index: -1")))))
