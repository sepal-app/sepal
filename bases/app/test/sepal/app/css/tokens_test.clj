(ns sepal.app.css.tokens-test
  "The design tokens are the single source of colour for the app, and two of
   them were adopted from the marketing site with contrast failures. These tests
   read the stylesheet itself, so a change that breaks WCAG breaks the build."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(def ^:private token-resource "sepal/app/css/tokens.css")

(defn tokens
  "Parse `--name: #RRGGBB;` pairs out of the token stylesheet."
  []
  (let [r (io/resource token-resource)]
    (assert (some? r) (str "missing " token-resource " on the classpath"))
    (into {}
          (map (fn [[_ k v]] [k (str/lower-case v)]))
          (re-seq #"--([a-z0-9-]+):\s*(#[0-9A-Fa-f]{6})\s*;" (slurp r)))))

(defn- channel [c]
  (let [c (/ (double c) 255.0)]
    (if (<= c 0.04045)
      (/ c 12.92)
      (Math/pow (/ (+ c 0.055) 1.055) 2.4))))

(defn relative-luminance [hex]
  (let [[r g b] (map #(Integer/parseInt (subs hex % (+ % 2)) 16) [1 3 5])]
    (+ (* 0.2126 (channel r))
       (* 0.7152 (channel g))
       (* 0.0722 (channel b)))))

(defn contrast
  "WCAG 2.x contrast ratio between two #rrggbb colours."
  [a b]
  (let [la (relative-luminance a)
        lb (relative-luminance b)
        hi (max la lb)
        lo (min la lb)]
    (/ (+ hi 0.05) (+ lo 0.05))))

(deftest test-contrast-helper-is-correct
  (testing "black on white is 21:1, the maximum"
    (is (< 20.99 (contrast "#000000" "#ffffff") 21.01)))
  (testing "a colour against itself is 1:1"
    (is (< 0.99 (contrast "#0f6b5c" "#0f6b5c") 1.01))))

(deftest test-text-tokens-meet-aa
  (testing "every text token clears 4.5:1 on every surface it is used on"
    (let [t (tokens)]
      (doseq [text ["color-text" "color-text-table" "color-text-nav"
                    "color-text-soft" "color-text-muted" "color-text-dim"
                    "color-brand"]
              surface ["color-page" "color-surface" "color-surface-alt"]]
        (let [fg (get t text)
              bg (get t surface)]
          (is (some? fg) (str "missing token --" text))
          (is (some? bg) (str "missing token --" surface))
          (when (and fg bg)
            (let [r (contrast fg bg)]
              (is (>= r 4.5)
                  (format "--%s on --%s is %.2f:1, needs 4.5" text surface r)))))))))

(deftest test-brand-on-its-own-tint-meets-aa
  (testing "the active nav item sets brand text on brand tint"
    (let [t (tokens)
          r (contrast (get t "color-brand") (get t "color-brand-tint"))]
      (is (>= r 4.5) (format "brand on brand-tint is %.2f:1, needs 4.5" r)))))

(deftest test-control-boundaries-meet-non-text-minimum
  (testing "WCAG 1.4.11 requires 3:1 for the boundary of a UI component"
    (let [t (tokens)]
      (doseq [border ["color-control" "color-field"]]
        (let [c (get t border)]
          (is (some? c) (str "missing token --" border))
          (when c
            (let [r (contrast c (get t "color-surface"))]
              (is (>= r 3.0)
                  (format "--%s on surface is %.2f:1, needs 3.0" border r)))))))))

(deftest test-semantic-badge-colours-meet-aa
  (testing "each badge foreground clears AA on its own background"
    (let [t (tokens)]
      (doseq [[fg bg] [["color-ok" "color-ok-bg"]
                       ["color-info" "color-info-bg"]
                       ["color-danger" "color-danger-bg"]
                       ["color-neutral" "color-neutral-bg"]]]
        (let [f (get t fg)
              b (get t bg)]
          (is (some? f) (str "missing token --" fg))
          (is (some? b) (str "missing token --" bg))
          (when (and f b)
            (let [r (contrast f b)]
              (is (>= r 4.5)
                  (format "--%s on --%s is %.2f:1, needs 4.5" fg bg r)))))))))

(deftest test-failing-tokens-are-gone
  (testing "the two tokens inherited from marketing/site.css that fail AA for
            small text must not exist, not merely go unused"
    (let [t (tokens)]
      (is (nil? (get t "color-text-faint"))
          "--color-text-faint (#7D908B) is 3.28:1 and fails at 12px")
      (is (not-any? #(= "#9aaca7" %) (vals t))
          "#9AACA7 is 2.31:1 and fails everything"))))
