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

(def ^:private class-string-line
  "A line holding only a string of lowercase class-like tokens, plus closing
   delimiters — a continuation of a multi-line class expression. Docstrings are
   excluded by requiring lowercase throughout: \"Export modal component for CSV
   downloads.\" is also a lone string line, and matching it made this gate
   report 27 findings of which 3 were real.

   It has to allow several strings on one line: an `html/attr` call wraps, and
   the line carrying \"rounded-md\" \"text-white\" \"bg-green-700\" was invisible to
   the single-string version of this pattern."
  #"^\s*(?:\"[a-z0-9 :/\[\]()%.,#-]*\"\s*)+[\s)}\]]*$")

(defn- class-lines
  "Lines that plausibly carry class names, with their 1-based numbers.

   Scanning every line instead matches Clojure code — `(defn table`,
   `table/card-table`, `:as select` — and drowns the real findings. Precision
   matters more than reach here: a gate that cries wolf gets ignored."
  [f]
  (->> (str/split-lines (slurp f))
       (map-indexed (fn [i l] [(inc i) l]))
       (filter (fn [[_ l]]
                 (or (str/includes? l ":class")
                     (str/includes? l "html/attr")
                     (re-matches class-string-line l))))))

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

(defn- quoted-strings
  "The contents of every double-quoted string on the line. A class name only
   ever appears inside one; scanning the whole line matches Clojure symbols
   too — `[:div {:class \"spl-table-scroll\"} table]` was reported as a use of
   the DaisyUI `table` class because of the parameter at the end."
  [line]
  (map second (re-seq #"\"([^\"]*)\"" line)))

(defn- uses-class?
  "True when `line` uses `cls` as a whole class token inside a string literal.

   The lookbehind excludes `-` and word characters, so `spl-btn--primary` does
   not count as a use of `btn-primary`."
  [line cls]
  (let [pat (re-pattern (str "(?<![-\\w])" (java.util.regex.Pattern/quote cls) "(?![-\\w])"))]
    (boolean (some #(re-find pat %) (quoted-strings line)))))

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

(deftest test-no-opacity-suffixed-theme-colours
  (testing "DaisyUI's theme colours also appear with an opacity suffix, which
            exact-token matching does not catch. This one scans every line —
            `base-content/70` cannot occur in Clojure code, so there is no
            false-positive risk and no need to guess which lines hold classes."
    (doseq [f (clj-sources)
            [n line] (->> (str/split-lines (slurp f))
                          (map-indexed (fn [i l] [(inc i) l])))]
      (is (not (re-find #"base-(100|200|300|content)/\d+" line))
          (format "%s:%d uses an opacity-suffixed DaisyUI colour"
                  (.getPath ^java.io.File f) n)))))

(deftest test-daisyui-is-not-a-dependency
  (is (not (str/includes? (slurp "bases/app/package.json") "daisyui"))))

(deftest test-dead-tailwind-config-is-gone
  (testing "it was never loaded — no @config directive anywhere, and
            vite.config.js registers only the plugin — and it mixed an ESM
            import with module.exports inside a type:module package"
    (is (not (.exists (io/file "bases/app/tailwind.config.js"))))))

(defn- defined-spl-classes
  "Every `spl-` class the stylesheets define a rule for."
  []
  (->> ["bases/app/src/sepal/app/css/components.css"
        "bases/app/src/sepal/app/css/tokens.css"]
       (mapcat #(re-seq #"\.(spl-[A-Za-z0-9_-]+)" (slurp %)))
       (map second)
       set))

(defn- used-spl-classes
  "Every `spl-` token that appears inside a class literal in the markup, with
   the file and line it came from.

   Only the string that follows `:class`, or the arguments of an `html/attr`
   call, count. Scanning whole lines matches Clojure symbols and prose."
  []
  (for [f (clj-sources)
        :let [src (slurp f)]
        m (re-seq #"(?::class\s+\"([^\"]*)\")|(?:html/attr\s+((?:\"[^\"]*\"\s*)+))"
                  src)
        :let [[whole one many] m]
        lit (if one [one] (map second (re-seq #"\"([^\"]*)\"" (or many ""))))
        tok (str/split lit #"\s+")
        :when (str/starts-with? tok "spl-")]
    [(.getPath ^java.io.File f)
     (inc (count (re-seq #"\n" (subs src 0 (str/index-of src whole)))))
     tok]))

(deftest test-every-spl-class-has-a-rule
  (testing "a name in the markup with no rule behind it is invisible: `spl-link`
            was on 24 elements with none, so every accession code and taxon name
            rendered as plain body text, and `spl-selectw-40` — two classes
            glued together without a space — matched nothing at all"
    (let [defined (defined-spl-classes)]
      (is (contains? defined "spl-link"))
      (doseq [[path line tok] (used-spl-classes)]
        (is (contains? defined tok)
            (format "%s:%d uses %s, which no stylesheet defines" path line tok))))))
