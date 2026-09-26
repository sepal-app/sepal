(ns sepal.i18n.interface
  "Translation with GNU gettext catalogs.

  Strings are English msgids at the call site. `bin/i18n-extract` finds them
  by the names tr, trc, trn and N_, so call these by name rather than through
  an alias or a higher-order function. Placeholders are %1, %2, ... in
  argument order.

  With no catalog bound, or no usable entry for a msgid, every function returns
  the English: a missing translation is never blank."
  (:require [sepal.i18n.core :as core]))

(def source-locale
  "The language the msgids are written in. It has no catalog: a user who
  chooses it gets the msgids."
  "en")

(def ^:dynamic *locale*
  "The resolved locale, e.g. \"es\", or nil for English."
  nil)

(def ^:dynamic *catalog*
  "The Catalog for *locale*, or nil for English."
  nil)

(defn- tr* [ctx msgid nargs a b c more]
  (let [m (core/lookup *catalog* ctx msgid)]
    (core/render (or m (core/compiled-msgid msgid)) nargs a b c more)))

(defn tr
  "Translate msgid, filling %1, %2, ... from args."
  ([msgid]
   (let [m (core/lookup *catalog* nil msgid)]
     (if (string? m) m (if m (core/render m 0 nil nil nil nil) msgid))))
  ([msgid a] (tr* nil msgid 1 a nil nil nil))
  ([msgid a b] (tr* nil msgid 2 a b nil nil))
  ([msgid a b c] (tr* nil msgid 3 a b c nil))
  ([msgid a b c & more] (tr* nil msgid (+ 3 (count more)) a b c more)))

(defn trc
  "Translate msgid in the context ctx, which becomes the PO msgctxt. Use it
  where the English alone is ambiguous: \"Code\" for an accession code."
  ([ctx msgid]
   (let [m (core/lookup *catalog* ctx msgid)]
     (if (string? m) m (if m (core/render m 0 nil nil nil nil) msgid))))
  ([ctx msgid a] (tr* ctx msgid 1 a nil nil nil))
  ([ctx msgid a b] (tr* ctx msgid 2 a b nil nil))
  ([ctx msgid a b c & more] (tr* ctx msgid (+ 3 (count more)) a b c more)))

(defn- trn* [singular plural n nargs a b c more]
  (let [p (core/lookup *catalog* nil singular)
        compiled (if (instance? sepal.i18n.core.Plural p)
                   (core/plural-form *catalog* p n)
                   (core/compiled-msgid (if (= 1 n) singular plural)))]
    (core/render compiled nargs a b c more)))

(defn trn
  "Translate a count-dependent message, choosing the form for n. With no
  further arguments %1 is n; pass them to fill %1 with, say, a formatted
  number instead."
  ([singular plural n] (trn* singular plural n 1 n nil nil nil))
  ([singular plural n a] (trn* singular plural n 1 a nil nil nil))
  ([singular plural n a b] (trn* singular plural n 2 a b nil nil))
  ([singular plural n a b c & more]
   (trn* singular plural n (+ 3 (count more)) a b c more)))

(defn N_
  "Return msgid unchanged, marking it for extraction. For English held in data
  evaluated at load, before any catalog is bound; call tr on the value when it
  is rendered."
  [msgid]
  msgid)

(defn parse-catalog
  "Compile PO text into a catalog for locale."
  [locale text]
  (core/parse-catalog locale text))

(defonce ^:private catalogs (atom {}))

(defn load-catalogs!
  "Load every i18n/<locale>.po on the classpath. Call once at startup; throws
  if a catalog does not parse. Tests pass {locale catalog} to install their
  own."
  ([]
   (reset! catalogs (core/load-catalogs)))
  ([locale->catalog]
   (reset! catalogs locale->catalog)))

(defn catalog
  "The loaded catalog for locale, or nil."
  [locale]
  (get @catalogs locale))

(defn available-locales
  "The locales with a loaded catalog, sorted."
  []
  (sort (keys @catalogs)))

(defn resolve-locale
  "The locale to use for an Accept-Language header: the first in q order with a
  catalog in available, or nil for English."
  ([header]
   (core/resolve-locale header (set (keys @catalogs))))
  ([header available]
   (core/resolve-locale header available)))

(defmacro with-locale
  "Evaluate body with locale's catalog bound. A locale with no catalog binds
  English."
  [locale & body]
  `(let [c# (catalog ~locale)]
     (binding [*catalog* c#
               *locale* (when c# ~locale)]
       ~@body)))

(defn display-name
  "A locale's name in its own language, capitalised: \"Español\" for es."
  [locale]
  (let [l (java.util.Locale/forLanguageTag (.replace ^String locale "_" "-"))
        s (.getDisplayName l l)]
    (if (seq s)
      (str (.toUpperCase (subs s 0 1) l) (subs s 1))
      locale)))

(defn java-locale
  "*locale* as a java.util.Locale, for date and number formatting."
  ^java.util.Locale []
  (if-let [l *locale*]
    (java.util.Locale/forLanguageTag (.replace ^String l "_" "-"))
    java.util.Locale/ENGLISH))

(defn format-number
  "n with the grouping separator of *locale*: \"1,284\" in English, \"1.284\"
  in Spanish."
  [n]
  (.format ^java.text.Format (java.text.NumberFormat/getIntegerInstance (java-locale))
           ^Object n))

(defn fill
  "A translated string as a vector of its text and `parts`, with each %n
  replaced by the nth part. For a sentence that has markup inside it:

    (fill (tr \"%1 matches synonym %2\") [:a ...] [:i ...])

  so the translation decides where the markup goes."
  [s & parts]
  (let [parts (vec parts)]
    (into []
          (comp (map (fn [[text idx]]
                       (if idx (get parts (dec (parse-long idx)) (str "%" idx)) text)))
                (remove #(= "" %)))
          (map (fn [[whole n]] (if n [nil n] [whole nil]))
               (re-seq #"%(\d+)|[^%]+|%" s)))))
