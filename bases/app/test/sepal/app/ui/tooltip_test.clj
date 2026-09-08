(ns sepal.app.ui.tooltip-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.tooltip :as tooltip])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup]
  (Jsoup/parseBodyFragment (chassis/html hiccup)))

(deftest test-tip-is-appended-to-the-control
  (let [el (parse (tooltip/wrap [:button {:aria-label "Dismiss"} [:svg]] "Dismiss"))
        tip (.selectFirst el "button > .spl-tip")]
    (is (some? tip) "the tip is a child of the control, not a sibling")
    (is (= "Dismiss" (.text tip)))))

(deftest test-control-becomes-the-host
  (testing "the control itself hosts the tip, so nothing wraps it and no
            layout changes"
    (is (some? (.selectFirst (parse (tooltip/wrap [:button {} [:svg]] "X"))
                             "button.spl-tip-host")))))

(deftest test-existing-classes-survive
  (doseq [class ["spl-btn spl-btn--icon" ["spl-btn" "spl-btn--icon"]]]
    (let [el (parse (tooltip/wrap [:button {:class class} [:svg]] "X"))]
      (is (some? (.selectFirst el "button.spl-btn.spl-btn--icon.spl-tip-host"))
          (str "lost a class given as " (pr-str class))))))

(deftest test-existing-children-survive
  (let [el (parse (tooltip/wrap [:button {} [:svg {:id "glyph"}]] "X"))]
    (is (some? (.selectFirst el "#glyph")))))

(deftest test-control-without-an-attrs-map
  (let [el (parse (tooltip/wrap [:button [:svg {:id "glyph"}]] "X"))]
    (is (some? (.selectFirst el "button.spl-tip-host")))
    (is (some? (.selectFirst el "#glyph")))))

(deftest test-other-attributes-survive
  (let [el (parse (tooltip/wrap [:button {:type "button" :aria-label "X"} [:svg]] "X"))
        button (.selectFirst el "button")]
    (is (= "button" (.attr button "type")))
    (is (= "X" (.attr button "aria-label")))))

(deftest test-tip-is-hidden-from-assistive-tech
  (testing "the text repeats the control's aria-label; described-by a copy of
            the accessible name only makes a screen reader say it twice"
    (let [tip (.selectFirst (parse (tooltip/wrap [:button {:aria-label "X"} [:svg]] "X"))
                            ".spl-tip")]
      (is (= "true" (.attr tip "aria-hidden")))
      (is (str/blank? (.attr tip "id"))
          "no id, so nothing can point aria-describedby at it"))))

(deftest test-side-selects-a-placement-modifier
  (doseq [side ["right" "left" "top" "bottom"]]
    (is (some? (.selectFirst (parse (tooltip/wrap [:button {} [:svg]] "X" :side side))
                             (str ".spl-tip.spl-tip--" side)))
        (str "no modifier for side " side))))

(deftest test-default-side-is-bottom
  (is (some? (.selectFirst (parse (tooltip/wrap [:button {} [:svg]] "X"))
                           ".spl-tip--bottom"))))

(deftest test-an-unknown-side-is-rejected
  (testing "a typo would silently render an unplaced tip stacked on the
            control, which reads as a rendering bug rather than a typo"
    (is (thrown? AssertionError (tooltip/wrap [:button {} [:svg]] "X" :side "middle")))))
