(ns sepal.code-template.interface
  "Named-token templates for accession and material codes.

  Text work only: parsing a template, rendering one, recognising a code that
  fits its shape, and deriving the next number from codes already issued. The
  queries that fetch those codes live in the accession and material
  components, which is what keeps this namespace pure."
  (:require [clojure.string :as str]
            [sepal.error.interface :as error.i])
  (:import [java.time LocalDate]
           [java.util.regex Pattern]))

(def ^:private date-tokens
  {"year" {:kind :year :width 4}
   "year2" {:kind :year2 :width 2}
   "month" {:kind :month :width 2}
   "day" {:kind :day :width 2}})

(def ^:private seq-pattern #"^seq(?::(0+))?$")

(defn- sequence-part?
  "Is this the part the number goes in? `{seq}` renders it as digits,
  `{letter}` as letters."
  [{:keys [kind]}]
  (contains? #{:seq :letter} kind))

(defn- token->part [body]
  (if-let [[_ zeros] (re-matches seq-pattern body)]
    {:kind :seq :width (count zeros)}
    (or (get date-tokens body)
        (when (= "letter" body) {:kind :letter})
        (error.i/error ::unknown-token (str "Unknown token {" body "}")))))

(defn parse
  "A template string to its parts, or an error naming what is wrong.

  A template needs exactly one sequence token, `{seq}` or `{letter}`. There is
  no escape for a literal brace: no code convention contains one, and leaving
  escaping out keeps this to a single pass."
  [template]
  (if (str/blank? template)
    (error.i/error ::empty-template "A template cannot be empty")
    (loop [i 0
           parts []]
      (let [open (str/index-of template "{" i)]
        (cond
          (nil? open)
          (let [parts (cond-> parts
                        (< i (count template))
                        (conj {:kind :literal :text (subs template i)}))
                seqs (count (filter sequence-part? parts))]
            (cond
              (zero? seqs) (error.i/error ::no-seq-token
                                          "A template needs a {seq} or {letter} token")
              (> seqs 1) (error.i/error ::many-seq-tokens
                                        "A template needs exactly one {seq} or {letter} token")
              :else parts))

          :else
          (let [close (str/index-of template "}" open)]
            (if (nil? close)
              (error.i/error ::unclosed-brace "A { is missing its closing }")
              (let [part (token->part (subs template (inc open) close))]
                (if (error.i/error? part)
                  part
                  (recur (inc close)
                         (cond-> parts
                           (< i open) (conj {:kind :literal :text (subs template i open)})
                           :always (conj part))))))))))))

(defn- pad [n width]
  (let [s (str n)]
    ;; A number past its width widens the code rather than truncating or
    ;; wrapping, so 10000 in {seq:0000} is 10000 and not a collision.
    (if (< (count s) width)
      (str (str/join (repeat (- width (count s)) \0)) s)
      s)))

(defn- ->letters
  "1 is A, 26 is Z, 27 is AA: the way spreadsheet columns are numbered."
  [n]
  (loop [n n
         letters ()]
    (if (pos? n)
      (let [n (dec n)]
        (recur (quot n 26) (conj letters (char (+ (int \A) (mod n 26))))))
      (apply str letters))))

(defn- letters->n [letters]
  (reduce (fn [n c] (+ (* n 26) (inc (- (int c) (int \A))))) 0 letters))

(defn- render-part [{:keys [kind text width]} ^LocalDate date n]
  (case kind
    :literal text
    :year (pad (.getYear date) 4)
    :year2 (pad (mod (.getYear date) 100) 2)
    :month (pad (.getMonthValue date) 2)
    :day (pad (.getDayOfMonth date) 2)
    :seq (pad n width)
    :letter (->letters n)))

(defn render
  "Parts, a date and a number to a code string."
  [parts ^LocalDate date n]
  (when-not (error.i/error? parts)
    (str/join (map #(render-part % date n) parts))))

(defn- ->pattern
  "A regex for `parts`. With a date, the date tokens are pinned to that date's
  values; without one they are a digit run of the right width, so a code from
  any year still matches the shape."
  [parts ^LocalDate date capture-seq?]
  (re-pattern
    (str/join
      (for [{:keys [kind width] :as part} parts]
        (case kind
          :literal (Pattern/quote (:text part))
          (:seq :letter) (let [body (if (= :letter kind)
                                      "[A-Z]+"
                                      (str "\\d{" (max 1 width) ",}"))]
                           (if capture-seq? (str "(" body ")") body))
          (if date
            (Pattern/quote (render-part part date 0))
            (str "\\d{" width "}")))))))

(defn matches?
  "Does `code` fit this template's shape, whatever date it carries?"
  [parts code]
  (boolean
    (when (and (not (error.i/error? parts)) (some? code))
      (re-matches (->pattern parts nil false) (str code)))))

(defn scan-prefix
  "The leading literal with date tokens expanded, for a SQL `like`. Empty when
  the template opens with its sequence token, which means the scan reads the
  whole column."
  [template ^LocalDate date]
  (let [parts (parse template)]
    (when-not (error.i/error? parts)
      (->> parts
           (take-while (complement sequence-part?))
           (map #(render-part % date 0))
           (str/join)))))

(defn next-code
  "The next code for `template`, derived from the codes already issued.

  Only codes matching the template with *today's* date values count, so the
  sequence restarts on its own when the year rolls over. The highest such
  number plus one: a gap below the maximum is never reused. Returns nil for a
  blank or unparseable template, so a broken setting degrades to an empty
  field rather than a 500."
  [template codes ^LocalDate date]
  (let [parts (parse template)]
    (when-not (error.i/error? parts)
      (let [rx (->pattern parts date true)
            ->n (if (some #(= :letter (:kind %)) parts) letters->n parse-long)
            numbers (keep (fn [code]
                            (when-let [[_ n] (re-matches rx (str code))]
                              (->n n)))
                          codes)]
        (render parts date (inc (reduce max 0 numbers)))))))

(defn config
  "A `settings.i/get-values db \"codes\"` map to the per-resource config.

  Every `codes.` setting is a seeded row, so there are no defaults here: an
  absent row means off, or no separator."
  [settings]
  (letfn [(for-resource [resource]
            {:template (get settings (str "codes." resource "_template"))
             :strict? (= "1" (get settings (str "codes." resource "_strict")))})]
    {:accession (for-resource "accession")
     :material (for-resource "material")
     :separator (get settings "codes.material_separator")}))

(defn full-code
  "A material's full code: its accession's code, the separator and its own
  code. A nil separator is the same as an empty one."
  [separator accession-code material-code]
  (str accession-code separator material-code))

(defn- digit-at-edge?
  "Does what `part` renders start (or end, with `end?`) with a digit?"
  [{:keys [kind text]} end?]
  (case kind
    :literal (some-> (if end? (last text) (first text)) Character/isDigit)
    :letter false
    true))

(defn runs-together?
  "Would a full code built from these templates put a digit either side of an
  empty separator, as `2026.0001` and `1` make `2026.00011`? False when either
  template is blank or unparseable, because then there is nothing to judge."
  [accession-template separator material-template]
  (let [accession (parse accession-template)
        material (parse material-template)]
    (boolean
      (and (empty? separator)
           (not (error.i/error? accession))
           (not (error.i/error? material))
           (digit-at-edge? (last accession) true)
           (digit-at-edge? (first material) false)))))
