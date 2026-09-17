(ns sepal.taxon.interface.name
  "Splitting a scientific name into the parts that are italicised and the parts
   that are not.

   This is botanical convention rather than styling: the hybrid marker, the
   infraspecific connecting terms (subsp., var., f.) and a cultivar epithet all
   stay upright while the genus and specific epithet are italic. Roughly one
   name in nine in the WFO reference taxonomy contains something that must stay
   upright, so this is the common case and not an edge.

   Returns data, not markup — the app base turns these segments into Hiccup.
   The taxon `author` column is separate and never part of this string."
  (:require [clojure.string :as str]))

(def ^:private upright-terms
  "Fragments that stay upright wherever they appear inside a name."
  [" × " " subsp. " " var. " " f. "])

(defn- split-cultivar
  "ICNCP writes the cultivar epithet in single quotes. Match from the FIRST
   quote to the LAST quote rather than pairing greedily, because an epithet may
   itself contain an apostrophe — 'Nuccio's Pearl' is a real cultivar.

   Returns [botanical-part epithet-or-nil]."
  [s]
  (let [start (str/index-of s \')
        end (str/last-index-of s \')]
    (if (and start end (> end start))
      [(subs s 0 start) (subs s start (inc end))]
      [s nil])))

(defn- split-upright
  "Split the botanical part around the upright connecting terms, earliest first."
  [s]
  (loop [remaining s
         acc []]
    (if (str/blank? remaining)
      acc
      (if-let [[term idx] (->> upright-terms
                               (keep (fn [t]
                                       (when-let [i (str/index-of remaining t)]
                                         [t i])))
                               (sort-by second)
                               first)]
        (recur (subs remaining (+ idx (count term)))
               (cond-> acc
                 (pos? idx) (conj {:text (subs remaining 0 idx) :role :scientific})
                 :always (conj {:text term :role :upright})))
        (conj acc {:text remaining :role :scientific})))))

(defn segments
  "Split a taxon name into `{:text :role}` maps, in order.

   `:role` is `:scientific` for the parts set in italic and `:upright` for the
   parts that are not. Returns `[]` for nil or blank input."
  [s]
  (if (str/blank? s)
    []
    (let [[botanical epithet] (split-cultivar s)]
      (cond-> (split-upright botanical)
        epithet (conj {:text epithet :role :upright})))))

(def ^:private infraspecific-ranks
  "The connecting term a name carries, and the rank it makes it."
  {" subsp. " :subspecies
   " var. " :variety
   " f. " :form
   " subvar. " :subvariety
   " subf. " :subform
   " convar. " :convariety})

(def ^:private rank-suffixes
  "Endings the botanical code reserves for a rank, longest first so `-oideae`
   is tested before `-eae`. These apply to a one-word name: `Rosaceae` is a
   family, not a genus that happens to end that way."
  [["aceae" :family]
   ["oideae" :subfamily]
   ["ineae" :suborder]
   ["ales" :order]
   ["inae" :subtribe]
   ["eae" :tribe]])

(def hybrid-marker
  "The multiplication sign, U+00D7. Not the letter x, and not the mathematical
   operator U+2715."
  "\u00d7")

(def ^:private typed-markers
  "What someone reaches for when the keyboard has no ×."
  #{"x" "X"})

(defn normalize-hybrid-marker
  "Rewrite a typed x to the hybrid marker it stands for.

   The WFO reference taxonomy writes × in all 6,503 of its hybrid names and a
   standalone x in none, so this is what a name has to look like to match one
   — and the letter is what a keyboard offers.

   Only a standalone token is a marker. Ilex and Rumex carry an x that belongs
   to the word, and rewriting that would be a bug. Runs of whitespace collapse
   to single spaces, which is the same normalising the name needs anyway."
  [s]
  (when s
    (if (str/blank? s)
      s
      (->> (str/split (str/trim s) #"\s+")
           (map #(if (typed-markers %) hybrid-marker %))
           (str/join " ")))))

(defn hybrid-marker-variants
  "The ways this name might have been written, differing only in the hybrid
   marker.

   The multiplication sign is the correct character and is what the WFO
   reference taxonomy stores, but a keyboard offers a letter x and that is what
   people type. A lookup by name has to find the row either way.

   Only a standalone token is a marker — `Ilex` and `Rumex` contain an x that
   is part of the word, and replacing that would be a bug. Returns the name
   unchanged in a one-element vector when it carries no marker."
  [s]
  (if (str/blank? s)
    []
    (let [swap (fn [marker]
                 (->> (str/split (str/trim s) #"\s+")
                      (map #(if (#{"x" "×"} %) marker %))
                      (str/join " ")))]
      (->> [(str/trim s) (swap "×") (swap "x")]
           (distinct)
           (vec)))))

(defn parent-name
  "The name of the taxon this one sits under, or nil when the name does not
   say.

   Strip the last thing the name adds: a cultivar epithet leaves the botanical
   part, an infraspecific term leaves what came before it, and a binomial
   leaves its genus. A one-word name — a genus, a family, an order — names no
   parent, because nothing above it can be read off the name itself."
  [s]
  (when-not (str/blank? s)
    (let [s (str/trim s)
          [botanical epithet] (split-cultivar s)
          botanical (str/trim botanical)]
      (or
        ;; A cultivar hangs off the whole botanical name before its epithet:
        ;; `Acer palmatum 'Bloodgood'` is a cultivar of the species, and
        ;; `Rosa 'Peace'` one of the genus.
        (when epithet (not-empty botanical))

        ;; A Group's own epithet is the last word, so drop it as well:
        ;; `Rhododendron Ponticum Group` sits under the genus and
        ;; `Brassica oleracea Capitata Group` under the species.
        (when (re-find #"(?i)\bgroup$" s)
          (->> (str/split (str/replace botanical #"(?i)\s+group$" "") #"\s+")
               (remove str/blank?)
               (butlast)
               (str/join " ")
               (not-empty)))

        ;; The earliest connecting term: `X subsp. Y var. Z` hangs off
        ;; `X subsp. Y`, not off `X`.
        (when-let [idx (->> (keys infraspecific-ranks)
                            (keep #(str/index-of botanical %))
                            (sort)
                            (last))]
          (-> (subs botanical 0 idx) (str/trim) (not-empty)))

        ;; A binomial hangs off its genus. The hybrid marker is not a word.
        (let [words (->> (str/split botanical #"\s+")
                         (remove #{"×" "x" "+"})
                         (remove str/blank?))]
          (when (= 2 (count words))
            (first words)))))))

(defn guess-rank
  "The rank a name implies, or nil when it implies nothing.

   For a create form to fill in while you type, so it is deliberately quiet:
   anything it is not sure about returns nil and leaves the field alone.

   A quoted epithet is a cultivar under the ICNCP, and a trailing `Group` is a
   cultivar group. Otherwise the connecting term decides, then the reserved
   ending of a one-word name, then the word count — one word is a genus and
   two a species. A hybrid marker is not itself a rank: `Acer × freemanii` is
   a nothospecies, which is filed as a species, and the marker drops out of the
   count."
  [s]
  (when-not (str/blank? s)
    (let [s (str/trim s)
          [botanical epithet] (split-cultivar s)]
      (cond
        epithet :cultivar

        (re-find #"(?i)\bgroup$" s) :group

        :else
        (or (some (fn [[term rank]]
                    (when (str/includes? botanical term) rank))
                  infraspecific-ranks)
            (let [tokens (->> (str/split (str/trim botanical) #"\s+")
                              (remove str/blank?))
                  marker? (some #{"×" "x" "X" "+"} tokens)
                  ;; A formula names the parents rather than the hybrid:
                  ;; `Acer rubrum × saccharinum` abbreviates `Acer rubrum ×
                  ;; Acer saccharinum`. Both parents are species, so the cross
                  ;; is one too. Read the rank off the first parent, which is
                  ;; the only part written out in full.
                  before (take-while (complement #{"×" "x" "X" "+"}) tokens)
                  words (remove #{"×" "x" "X" "+"} tokens)]
              (cond
                ;; Two names joined by a marker. `Acer × freemanii` is not
                ;; this: its left side is a bare genus, which is a
                ;; nothospecies epithet hanging off it rather than a parent.
                (and marker? (= 2 (count before)))
                :species

                :else
                (case (count words)
                  1 (let [word (str/lower-case (first words))]
                      (or (some (fn [[suffix rank]]
                                  (when (str/ends-with? word suffix) rank))
                                rank-suffixes)
                          :genus))
                  2 :species
                  nil))))))))

(defn formula
  "The cross written out: parent names joined by the hybrid marker, in order.

   Lives here because this namespace already owns how a name is written, and
   because `\" × \"` is one of the upright terms above — so `segments` splits
   the result correctly with no further work, italicising each parent and
   leaving the marker upright.

   Blank names are dropped rather than rendered as an empty slot: a cross with
   one parent recorded reads as that parent, not as `\"× Cattleya\"`."
  [parent-names]
  (->> parent-names
       (remove str/blank?)
       (str/join (str " " hybrid-marker " "))))
