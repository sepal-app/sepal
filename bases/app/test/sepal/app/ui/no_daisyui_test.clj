(ns sepal.app.ui.no-daisyui-test
  "Principle 7 as an executable gate: colours, spacing and radii live in one
   file, and no Clojure file hardcodes any of them.

   These read the sources directly rather than rendered output, because a class
   on a branch nobody exercised in a test is exactly what drifts."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(defn- clj-sources []
  (->> (file-seq (io/file "bases/app/src"))
       (filter #(.isFile ^java.io.File %))
       (filter #(str/ends-with? (.getName ^java.io.File %) ".clj"))))

(defn- class-lines
  "Lines that carry a :class attribute, with their 1-based numbers."
  [f]
  (->> (str/split-lines (slurp f))
       (map-indexed (fn [i l] [(inc i) l]))
       (filter (fn [[_ l]] (str/includes? l ":class")))))

(def ^:private daisyui-classes
  "DaisyUI class names, matched as whole class tokens. Substring matching would
   flag our own replacements — `spl-btn--primary` contains `btn-`."
  ["btn" "btn-primary" "btn-secondary" "btn-ghost" "btn-outline" "btn-square"
   "btn-circle" "btn-sm" "btn-xs" "btn-lg" "btn-error" "btn-success"
   "btn-disabled" "btn-soft"
   "card" "card-body" "card-title" "card-actions" "card-compact" "card-border"
   "badge" "badge-sm" "badge-ghost" "badge-neutral" "badge-primary"
   "badge-accent" "badge-success" "badge-error" "badge-warning"
   "step-primary" "table" "table-zebra"
   "alert" "alert-info" "alert-warning" "alert-success" "alert-error"
   "modal" "modal-box" "modal-action" "modal-backdrop"
   "collapse" "collapse-arrow" "collapse-title" "collapse-content"
   "drawer" "drawer-toggle" "drawer-side" "drawer-content" "drawer-overlay"
   "tabs" "tabs-box" "tab" "tab-active" "navbar" "tooltip" "rounded-box"
   "menu" "steps" "steps-horizontal" "step" "join" "join-item" "divider"
   "validator" "validator-hint" "avatar" "loading" "toggle" "checkbox"
   "fieldset" "fieldset-legend"
   "select" "select-bordered" "select-sm" "select-md" "select-ghost"
   "input" "input-bordered" "input-sm" "input-md"
   "textarea" "textarea-bordered"
   "bg-base-100" "bg-base-200" "bg-base-300"
   "border-base-100" "border-base-200" "border-base-300"
   "text-base-content"])

(defn- uses-class?
  "True when `line` uses `cls` as a whole class token.

   The lookbehind excludes `-`, word characters and `:`. The first two keep
   `spl-btn--primary` from counting as a use of `btn-primary`; the colon keeps
   Hiccup element keywords out of it, since `[:select {:class …}]` names an
   HTML element rather than a DaisyUI class."
  [line cls]
  (boolean (re-find (re-pattern (str "(?<![-\\w:])" (java.util.regex.Pattern/quote cls) "(?![-\\w])"))
                    line)))

(deftest test-no-daisyui-component-classes
  (testing "every DaisyUI class was migrated to the spl- layer"
    (doseq [f (clj-sources)
            [n line] (class-lines f)
            cls daisyui-classes]
      (is (not (uses-class? line cls))
          (format "%s:%d still uses %s" (.getPath ^java.io.File f) n cls)))))

(deftest test-no-hardcoded-palette
  (testing "no Clojure file hardcodes a colour — the app had four unrelated
            accent families and 52 hardcoded grays before this"
    (doseq [f (clj-sources)
            [n line] (class-lines f)]
      (is (not (re-find #"(gray|indigo|blue|green|red|yellow|purple|slate)-[0-9]{2,3}" line))
          (format "%s:%d hardcodes a palette colour" (.getPath ^java.io.File f) n)))))

(deftest test-daisyui-is-not-a-dependency
  (is (not (str/includes? (slurp "bases/app/package.json") "daisyui"))))

(deftest test-dead-tailwind-config-is-gone
  (testing "it was never loaded — no @config directive anywhere, and
            vite.config.js registers only the plugin — and it mixed an ESM
            import with module.exports inside a type:module package"
    (is (not (.exists (io/file "bases/app/tailwind.config.js"))))))

(deftest test-spl-link-has-a-definition
  (testing "it was on 24 elements with no rule at all, so every accession code
            and taxon name rendered as plain body text"
    (is (str/includes? (slurp "bases/app/src/sepal/app/css/components.css")
                       ".spl-link"))))
