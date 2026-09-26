(ns sepal.malli.core
  (:require ;;[banzai.malli.core :as core]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [malli.core :as m]
    [malli.error :as me]
    [malli.experimental.time :as met]
    [malli.experimental.time.generator]
    [malli.registry :as mr]
    [sepal.i18n.interface :refer [N_ tr trn]]))

(defn init []
  (mr/set-default-registry!
    (mr/composite-registry
      (m/default-schemas)
      ;; Add the malli.experimental.time schema to the registry
      (met/schemas)
      ;; Add custom global schema here
      {:json
       [:or {:decode/store json/read-str
             :encode/store json/write-str}
        [:map-of :string :any]
        [:sequential [:map-of :string :any]]]
       ;; sqlite rowid
       :rowid [:int {:max 9,223,372,036,854,775,807  :min 0}]})))

(defn- message [msg]
  {:error/message {:en msg}})

(defn- bounds-fn
  "For :int, :double, :>=, :<= and friends: the type check, then the bound."
  [type-message pred]
  {:error/fn {:en (fn [{:keys [schema value]} _]
                    (let [{:keys [min max]} (m/properties schema)]
                      (cond
                        (not (pred value)) (tr type-message)
                        (and min max) (tr "should be between %1 and %2" min max)
                        min (tr "should be at least %1" min)
                        max (tr "should be at most %1" max))))}})

(defn- compare-fn [msgid]
  {:error/fn {:en (fn [{:keys [schema value]} _]
                    (if (number? value)
                      (tr msgid (first (m/children schema)))
                      (tr "should be a number")))}})

(def ^:private errors
  "malli's default messages, with the ones a form shows rewritten so they can
  be translated. Fixed messages are marked with N_ and translated by humanize;
  messages with a number in them call tr or trn themselves."
  (merge
    me/default-errors
    {::me/unknown (message (N_ "unknown error"))
     ::m/missing-key (message (N_ "missing required key"))
     ::m/invalid-type (message (N_ "invalid type"))
     ::m/extra-key (message (N_ "disallowed key"))
     'string? (message (N_ "should be a string"))
     'number? (message (N_ "should be a number"))
     'integer? (message (N_ "should be an integer"))
     'int? (message (N_ "should be an integer"))
     'pos-int? (message (N_ "should be a positive whole number"))
     'nat-int? (message (N_ "should be zero or a positive whole number"))
     'boolean? (message (N_ "should be true or false"))
     :re (message (N_ "is not in the expected format"))
     :boolean (message (N_ "should be true or false"))
     :uuid (message (N_ "should be a uuid"))
     :string {:error/fn {:en (fn [{:keys [schema value]} _]
                               (let [{:keys [min max]} (m/properties schema)]
                                 (cond
                                   (not (string? value)) (tr "should be a string")
                                   (and min (= min max)) (trn "should be %1 character" "should be %1 characters" min)
                                   (and min (< (count value) min)) (trn "should be at least %1 character" "should be at least %1 characters" min)
                                   max (trn "should be at most %1 character" "should be at most %1 characters" max))))}}
     :int (bounds-fn "should be an integer" int?)
     :double (bounds-fn "should be a number" number?)
     :> (compare-fn "should be larger than %1")
     :>= (compare-fn "should be at least %1")
     :< (compare-fn "should be smaller than %1")
     :<= (compare-fn "should be at most %1")
     :enum {:error/fn {:en (fn [{:keys [schema]} _]
                             (tr "should be one of: %1"
                                 (str/join ", " (map #(if (keyword? %) (name %) (str %))
                                                     (m/children schema)))))}}}))

(defn humanize
  "malli.error/humanize in the current locale. Every string in the result goes
  through tr, which translates malli's fixed messages and a schema's own
  :error/message when it is marked with N_, and passes anything else through."
  [explanation]
  (walk/postwalk #(if (string? %) (tr %) %)
                 (me/humanize explanation {:errors errors})))

(defn humanize-coercion-ex
  "Return the humanized error of a malli.core/coercion exception."
  [ex]
  (-> ex ex-data :data :explain humanize))
