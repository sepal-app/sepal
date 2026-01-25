(ns sepal.app.cli.routes
  "CLI command to print the route tree."
  (:require [clojure.string :as str]
            [reitit.core :as r]
            [sepal.malli.interface :as malli.i]))

(defn- format-route [[path data]]
  (let [name (:name data)
        ;; Handler methods, if specified individually
        methods (->> [:get :post :put :delete :patch :head :options]
                     (filter #(contains? data %))
                     (map (comp str/upper-case clojure.core/name)))]
    {:path path
     :name (some-> name str)
     :methods (if (seq methods) (str/join ", " methods) "ALL")}))

(defn- print-routes-table [routes]
  (let [formatted (map format-route routes)
        max-path (apply max 4 (map #(count (:path %)) formatted))
        max-name (apply max 4 (map #(count (or (:name %) "")) formatted))]
    (println (format (str "%-" max-path "s  %-" max-name "s  %s")
                     "PATH" "NAME" "METHODS"))
    (println (apply str (repeat (+ max-path max-name 12) "-")))
    (doseq [{:keys [path name methods]} formatted]
      (println (format (str "%-" max-path "s  %-" max-name "s  %s")
                       path (or name "-") methods)))))

(defn- build-tree
  "Build a trie from flattened routes."
  [routes]
  (reduce
    (fn [tree [path data]]
      (let [segments (remove empty? (str/split path #"/"))
            ;; Handle root path "/" specially
            segments (if (empty? segments) [""] segments)]
        (assoc-in tree (conj (vec segments) ::data) data)))
    {}
    routes))

(defn- print-tree*
  "Recursively print the route tree with indentation."
  [tree depth]
  (let [entries (->> tree
                     (filter (fn [[k _]] (not= k ::data)))
                     (sort-by (fn [[k _]] (str k))))]
    (doseq [[segment subtree] entries]
      (let [data (::data subtree)
            indent (apply str (repeat depth "  "))
            ;; Handle root path (empty segment) specially
            segment-str (if (= segment "") "/" (str "/" segment))
            name-str (when-let [n (:name data)] (str " → " n))]
        (println (str indent segment-str name-str))
        (print-tree* subtree (inc depth))))))

(defn- print-routes-tree [routes]
  (let [tree (build-tree routes)]
    (print-tree* tree 0)))

(defn print-route-tree
  "Print the application route tree.

   Options:
   - :format - :table (default) or :tree"
  ([] (print-route-tree {}))
  ([{:keys [format] :or {format :table}}]
   ;; Initialize malli before loading server (which has schemas using :time/instant)
   (malli.i/init)
   (require 'sepal.app.server)
   (let [routes-fn (ns-resolve 'sepal.app.server 'routes)
         router (r/router (routes-fn))
         routes (r/compiled-routes router)]
     (case format
       :table (print-routes-table routes)
       :tree (print-routes-tree routes)))))
