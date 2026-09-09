(ns sepal.app.ui.css-contract-test
  "The two things the markup and the stylesheets have to agree about: colours
   come from tokens.css and nowhere else, and every `spl-` class in the markup
   has a rule behind it.

   These read the sources directly rather than rendered output, because a class
   on a branch nobody exercised in a test is exactly what drifts.

   This file used to also gate against DaisyUI coming back — a class scanner, an
   opacity-suffix scanner, a package.json check and a tailwind.config.js check.
   DaisyUI has been gone long enough that guarding against its return is not
   worth what it cost: the class scanner alone ran one assertion per source line
   per DaisyUI class name, which was 10 seconds and most of the suite's
   assertion count."
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

(deftest test-no-hardcoded-palette
  (testing "no Clojure file hardcodes a colour — the app had four unrelated
            accent families and 52 hardcoded grays before this"
    (doseq [f (clj-sources)
            [n line] (class-lines f)]
      (is (not (re-find #"(gray|indigo|blue|green|red|yellow|purple|slate)-[0-9]{2,3}" line))
          (format "%s:%d hardcodes a palette colour" (.getPath ^java.io.File f) n)))))

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
