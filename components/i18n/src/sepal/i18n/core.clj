(ns sepal.i18n.core
  "Catalog compilation and the lookup hot path. JVM only: bb never loads this
  namespace, so interop and type hints belong here rather than in po.clj.

  tr runs about 60 times a page and almost every call takes no arguments, so
  that path allocates nothing: a nested map lookup returning a String."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [sepal.i18n.plural :as plural]
            [sepal.i18n.po :as po])
  (:import [java.net JarURLConnection URL]
           [java.util.concurrent ConcurrentHashMap]
           [java.util.function Function]))

(set! *warn-on-reflection* true)

;; A compiled message is a String when it has no %n placeholder, else a vector
;; of String segments and 0-based argument indexes: "Sent to %1" is
;; ["Sent to " 0]. A plural entry wraps one compiled message per form.
(deftype Plural [forms])

(defrecord Catalog [locale nplurals plural-index messages])

(defn compile-message [^String s]
  (if (neg? (.indexOf s "%"))
    s
    (let [m (re-matcher #"%(\d+)" s)]
      (loop [start 0
             segments []]
        (if (.find m)
          (let [i (dec (parse-long (.group m 1)))]
            (recur (.end m)
                   (cond-> segments
                     (< start (.start m)) (conj (subs s start (.start m)))
                     :always (conj (if (neg? i) (.group m) i)))))
          (if (seq segments)
            (cond-> segments
              (< start (count s)) (conj (subs s start)))
            s))))))

(defn- arg [i nargs a b c more]
  (if (< (long i) (long nargs))
    (case (long i)
      0 a
      1 b
      2 c
      (nth more (- (long i) 3)))
    ::missing))

(defn render
  "Interpolate a compiled message. nargs is how many of a, b, c and more are
  real arguments."
  [compiled nargs a b c more]
  (if (string? compiled)
    compiled
    (let [sb (StringBuilder.)]
      (doseq [seg compiled]
        (if (string? seg)
          (.append sb ^String seg)
          (let [v (arg seg nargs a b c more)]
            (if (= ::missing v)
              (-> sb (.append "%") (.append (inc (long seg))))
              (.append sb (str v))))))
      (.toString sb))))

;; English msgids with placeholders compile on first use. msgids are source
;; literals, so the key space is bounded by the program text. Only the
;; argument-taking path uses this; see sepal.i18n.interface.
(def ^:private ^ConcurrentHashMap msgid-cache (ConcurrentHashMap.))

(def ^:private compile-fn
  (reify Function
    (apply [_ s] (compile-message s))))

(defn compiled-msgid [^String msgid]
  (.computeIfAbsent msgid-cache msgid compile-fn))

(defn lookup [^Catalog catalog ctx msgid]
  (when catalog
    (get (get (.-messages catalog) ctx) msgid)))

(defn plural-form [^Catalog catalog ^Plural p n]
  (let [forms (.-forms p)
        i ((.-plural-index catalog) n)]
    (nth forms i (peek forms))))

;;; Loading

(def ^:private default-plural-forms "nplurals=2; plural=(n != 1);")

(defn- usable? [{:keys [msgid msgstr msgstr-plural flags obsolete?]}]
  (and (not= "" msgid)
       (not obsolete?)
       (not (contains? flags "fuzzy"))
       (if msgstr-plural
         (and (seq msgstr-plural) (every? seq msgstr-plural))
         (seq msgstr))))

(defn parse-catalog
  "Compile PO text into a Catalog for locale. Fuzzy, obsolete and untranslated
  entries are left out, so a lookup for them misses and returns the msgid."
  [locale text]
  (let [entries (po/parse text)
        header (some #(when (= "" (:msgid %)) %) entries)
        fields (some-> header :msgstr po/header-fields)
        {:keys [nplurals index]} (plural/parse (get fields "Plural-Forms"
                                                    default-plural-forms))]
    (->Catalog locale nplurals index
               (reduce (fn [m {:keys [msgctxt msgid msgstr msgstr-plural] :as e}]
                         (if (usable? e)
                           (assoc-in m [msgctxt msgid]
                                     (if msgstr-plural
                                       (->Plural (mapv compile-message msgstr-plural))
                                       (compile-message msgstr)))
                           m))
                       {}
                       entries))))

(defn- po-names
  "Names of the files in every i18n/ directory on the classpath: directories in
  development, the jar in a build."
  []
  (->> (enumeration-seq (.getResources (clojure.lang.RT/baseLoader) "i18n"))
       (mapcat (fn [^URL url]
                 (case (.getProtocol url)
                   "file" (map #(.getName ^java.io.File %) (.listFiles (io/file url)))
                   "jar" (let [conn ^JarURLConnection (.openConnection url)]
                           (->> (enumeration-seq (.entries (.getJarFile conn)))
                                (map #(.getName ^java.util.jar.JarEntry %))
                                (keep #(second (re-matches #"i18n/([^/]+)" %)))))
                   nil)))
       (distinct)))

(defn load-catalogs
  "Every i18n/<locale>.po on the classpath, as {locale Catalog}. Throws if one
  does not parse."
  []
  (into {}
        (keep (fn [name]
                (when-let [[_ locale] (re-matches #"(.+)\.po" name)]
                  [locale (parse-catalog locale (slurp (io/resource (str "i18n/" name))))])))
        (po-names)))

;;; Accept-Language

(defn- normalise-tag [tag]
  (let [[lang region] (str/split (str/lower-case tag) #"-" 2)]
    (if region
      [(str lang "_" (str/upper-case region)) lang]
      [lang lang])))

(defn- parse-accept-language [header]
  (->> (str/split header #",")
       (keep (fn [part]
               (let [[tag & params] (map str/trim (str/split part #";"))
                     q (reduce (fn [q p]
                                 (if-let [[_ v] (re-matches #"q=(.*)" p)]
                                   (or (parse-double v) (reduced nil))
                                   q))
                               1.0
                               params)]
                 (when (and q (pos? q) tag (re-matches #"[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*|\*" tag))
                   [tag q]))))
       (sort-by (comp - second))
       (map first)))

(defn resolve-locale
  "The first locale in an Accept-Language header, in q order, that has a
  catalog in available. nil means English: the header prefers English, names
  nothing available, or is missing."
  [header available]
  (when-not (str/blank? header)
    (reduce (fn [_ tag]
              (let [[full primary] (normalise-tag tag)]
                (cond
                  (or (= "*" tag) (= "en" primary)) (reduced nil)
                  (contains? available full) (reduced full)
                  (contains? available primary) (reduced primary)
                  :else nil)))
            nil
            (parse-accept-language header))))
