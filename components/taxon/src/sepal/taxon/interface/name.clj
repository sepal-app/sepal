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
