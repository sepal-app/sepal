(ns sepal.app.ui.location-path
  "A location shown as its path, Orchard › Row 3. Names aren't unique, and two
  orchards can each have a Row 3, so wherever a location's name is shown on
  another record it goes through here. Code-only displays don't, because codes
  are unique."
  (:require [clojure.string :as str]
            [sepal.location.interface :as location.i]))

(def separator " › ")

(defn text
  "The names of a root-first vector of locations, joined."
  [locations]
  (str/join separator (map :location/name locations)))

(defn by-id
  "{id path-text} for `ids`, in one query."
  [db ids]
  (update-vals (location.i/paths db (set (remove nil? ids))) text))

(defn location
  "The location with this id carrying its path as :location/path, root first,
  or nil. For a panel that shows one location, so the path rides along with
  the map it already passes."
  [db id]
  (when id
    (when-let [chain (get (location.i/paths db #{id}) id)]
      (assoc (last chain) :location/path chain))))

(defn markup
  "The path with the ancestors free to truncate, so a deep one never hides the
  location's own name."
  [locations]
  (let [ancestors (butlast locations)]
    [:span {:class "inline-flex min-w-0 max-w-full" :title (text locations)}
     (when (seq ancestors)
       [:span {:class "truncate"} (str (text ancestors) separator)])
     [:span {:class "shrink-0"} (:location/name (last locations))]]))
