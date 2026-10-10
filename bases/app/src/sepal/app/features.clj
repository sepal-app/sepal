(ns sepal.app.features
  "The sections a garden admin can turn off. A disabled feature keeps its data:
  its list, create and write routes answer 404, and its records render
  read-only. Taxa, accessions, material and locations cannot be turned off."
  (:require [sepal.app.globals :as g]
            [sepal.media.interface.permission :as media.perm]
            [sepal.observation.interface.permission :as observation.perm]
            [sepal.propagation.interface.permission :as propagation.perm]
            [sepal.search.interface :as search.i]
            [sepal.settings.interface :as settings.i]
            [sepal.tag.interface.permission :as tag.perm]))

(def all
  "Every feature that can be turned off, in the order Settings lists them."
  [:observations :propagation :media :tags])

(defn setting-key
  "The settings row holding a feature's flag. \"off\" turns it off; any other
  value, or no row, leaves it on."
  [feature]
  (str "features." (name feature)))

(defn disabled
  "The set of features the garden has turned off."
  [db]
  (let [values (settings.i/get-values db "features")]
    (into #{} (filter #(= "off" (get values (setting-key %)))) all)))

(defn enabled?
  "Whether `feature` is on for this request. nil names a section that cannot be
  turned off, so it is always on."
  [feature]
  (not (contains? g/*disabled-features* feature)))

(def ^:private owned-permissions
  "What a disabled feature takes from every role. View stays, for the
  read-only page."
  {:observations #{observation.perm/create observation.perm/edit observation.perm/delete}
   :propagation #{propagation.perm/create propagation.perm/edit propagation.perm/delete}
   :media #{media.perm/create media.perm/edit media.perm/delete}
   :tags #{tag.perm/create tag.perm/edit tag.perm/delete}})

(defn withheld?
  "Whether `permission` belongs to a feature that is turned off."
  [permission]
  (boolean (some #(contains? (owned-permissions %) permission) g/*disabled-features*)))

(def ^:private list-features
  "Column-picker lists that belong to a feature, by list key."
  {:observation :observations
   :propagation :propagation
   :tag :tags})

(defn list-enabled?
  "Whether the list whose columns are saved under `list-key` is available."
  [list-key]
  (enabled? (get list-features list-key)))

(def ^:private search-fields
  "Search fields that read a feature's data, by feature."
  {:tags #{"tag"}})

(defn- disabled-field? [field]
  (some #(contains? (search-fields %) field) g/*disabled-features*))

(defn field-options
  "A list's search fields, without those of features that are off."
  [resource-type]
  (remove #(disabled-field? (:key %)) (search.i/field-options resource-type)))

(defn parse-search
  "search.i/parse, with the filters of features that are off dropped."
  [q]
  (update (search.i/parse q) :filters #(vec (remove (comp disabled-field? :field) %))))
