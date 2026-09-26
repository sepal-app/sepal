(ns sepal.i18n.po
  "Reads GNU gettext PO files.

  Uses clojure.string only so that it runs unmodified under babashka, where
  bin/i18n-translate loads it. bin/i18n-check loads it under bb to keep that
  true: don't add Java interop or type hints here.

  The reader expects the canonical PO that xgettext, msgmerge and translation
  editors write, checked by `msgfmt -c` before it gets here. It throws on
  anything it does not understand rather than guessing."
  (:require [clojure.string :as str]))

(defn- fail [line-no msg]
  (throw (ex-info (str "PO parse error at line " line-no ": " msg)
                  {:line line-no})))

(defn- unescape [s line-no]
  (str/replace s #"\\(.)|\""
               (fn [[_ e]]
                 (case e
                   "n" "\n"
                   "t" "\t"
                   "r" "\r"
                   "\"" "\""
                   "\\" "\\"
                   nil (fail line-no "unescaped quote inside a string")
                   (fail line-no (str "unknown escape \\" e))))))

(defn- quoted
  "The unescaped content of a `\"...\"` token."
  [s line-no]
  (let [s (str/trim s)
        body (when (and (str/starts-with? s "\"")
                        (str/ends-with? s "\"")
                        (> (count s) 1))
               (subs s 1 (dec (count s))))
        trailing-backslashes (when body
                               (count (take-while #(= \\ %) (reverse body))))]
    (when (or (nil? body) (odd? trailing-backslashes))
      (fail line-no "unterminated string"))
    (unescape body line-no)))

(defn- keyword-line
  "Split `msgstr[1] \"...\"` into [field-key index string]."
  [line line-no]
  (let [[_ kw idx rest] (re-matches #"(msgctxt|msgid_plural|msgid|msgstr)(?:\[(\d+)\])?\s+(.*)" line)]
    (when-not kw
      (fail line-no (str "unrecognised line: " line)))
    [(case kw
       "msgctxt" :msgctxt
       "msgid" :msgid
       "msgid_plural" :msgid-plural
       "msgstr" (if idx :msgstr-plural :msgstr))
     (when idx (parse-long idx))
     (quoted rest line-no)]))

(defn- append-field [entry [k idx s]]
  (if (= k :msgstr-plural)
    (update entry :msgstr-plural (fnil #(assoc % idx (str (get % idx) s)) []))
    (update entry k str s)))

(defn- complete? [entry]
  (or (contains? entry :msgstr) (contains? entry :msgstr-plural)))

(defn- finish [entries entry]
  (if (contains? entry :msgid)
    (conj entries (dissoc entry ::field))
    entries))

(defn- comment-line [entry line]
  (let [text (str/trim (subs line (min 2 (count line))))]
    (cond
      (str/starts-with? line "#.") (update entry :extracted (fnil conj []) text)
      (str/starts-with? line "#:") (update entry :references (fnil into []) (str/split text #"\s+"))
      (str/starts-with? line "#,") (update entry :flags (fnil into #{}) (map str/trim) (str/split text #","))
      (str/starts-with? line "#|") (update entry :previous (fnil conj []) text)
      :else (update entry :comments (fnil conj []) (str/trim (subs line 1))))))

(defn parse
  "Parse PO text into a vector of entry maps, in file order. The header is the
  entry whose :msgid is \"\".

  Keys, each present only when the entry has it: :msgctxt, :msgid,
  :msgid-plural, :msgstr, :msgstr-plural (a vector), :flags (a set of strings),
  :extracted, :references, :comments, :previous (vectors of strings), and
  :obsolete? for #~ entries."
  [text]
  (loop [[line & more :as lines] (str/split-lines text)
         line-no 1
         entry {}
         entries []]
    (if (empty? lines)
      (finish entries entry)
      (let [obsolete? (str/starts-with? line "#~")
            line (if obsolete? (str/triml (subs line 2)) (str/trim line))]
        (cond
          (str/blank? line)
          (recur more (inc line-no) {} (finish entries entry))

          (and (str/starts-with? line "#") (not obsolete?))
          (let [[entries entry] (if (complete? entry)
                                  [(finish entries entry) {}]
                                  [entries entry])]
            (recur more (inc line-no) (comment-line entry line) entries))

          (str/starts-with? line "\"")
          (let [field (::field entry)]
            (when-not field
              (fail line-no "continuation line with no field before it"))
            (recur more (inc line-no)
                   (append-field entry (conj (pop field) (quoted line line-no)))
                   entries))

          :else
          (let [[k idx s :as field] (keyword-line line line-no)
                starts-new? (and (#{:msgctxt :msgid} k) (complete? entry))
                entries (if starts-new? (finish entries entry) entries)
                entry (cond-> (if starts-new? {} entry)
                        obsolete? (assoc :obsolete? true))]
            (recur more (inc line-no)
                   (-> entry
                       (append-field [k idx s])
                       (assoc ::field [k idx ""]))
                   entries)))))))

(defn header-fields
  "The `Name: value` pairs of a header entry's msgstr, as a map."
  [msgstr]
  (into {}
        (keep (fn [line]
                (when-let [[_ k v] (re-matches #"([^:]+):\s*(.*)" line)]
                  [(str/trim k) (str/trim v)])))
        (str/split-lines msgstr)))
