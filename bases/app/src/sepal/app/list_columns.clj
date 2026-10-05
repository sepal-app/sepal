(ns sepal.app.list-columns
  "Which of a list's columns a user sees. The choices arrive as an argument;
  nothing here reads the viewer or the request.")

(def lists
  "Every list with a column picker, by the key its choices are stored under."
  #{:accession :contact :location :material :observation :propagation :tag :taxon})

(def ^:private key-pattern #"[a-z][a-z0-9-]*")

(def ^:private max-keys
  "More than any list has columns, so a request carrying more is not a picker."
  50)

(defn hideable?
  "Priority 1 columns hold the record link and the narrow-screen summary, so
  they always show."
  [column]
  (> (or (:priority column) 1) 1))

(defn- visible? [overrides column]
  (let [k (:key column)]
    (if (and (hideable? column) (contains? overrides k))
      (true? (get overrides k))
      (not (:hidden? column)))))

(defn visible-columns
  "The columns to show, in table order. `overrides` maps a column key to true
  or false, or is nil when the user hasn't chosen. Overrides on a column that
  can't be hidden, and keys that match no column, are ignored."
  [columns overrides]
  (filterv (partial visible? overrides) columns))

(defn valid-keys?
  "Whether `ks`, as strings from a form, could all be column keys."
  [ks]
  (and (<= (count ks) max-keys)
       (every? #(and (string? %) (re-matches key-pattern %)) ks)))
