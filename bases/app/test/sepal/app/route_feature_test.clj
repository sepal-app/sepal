(ns sepal.app.route-feature-test
  "Every route that belongs to a feature says so, checked from the route table,
  so a route added to a feature's section is covered without a new test."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [reitit.core :as r]
            [reitit.ring]
            [sepal.app.server :as server]))

(defn- route-table []
  (r/routes (reitit.ring/router (server/routes) {:conflicts nil})))

(def ^:private sections
  "Each feature's own section. Every route under one belongs to it."
  {"/observation/" :observations
   "/propagation/" :propagation
   "/media/" :media
   "/tag/" :tags})

(def ^:private elsewhere
  "A feature's routes under another resource: its tab on a record, and its
  bulk actions."
  {"/material/bulk/observation/" :observations
   "/material/:id/observations/" :observations
   "/material/:id/observations/:observation-id/" :observations
   "/location/:id/observations/" :observations
   "/location/:id/observations/:observation-id/" :observations
   "/accession/:id/media/" :media
   "/location/:id/media/" :media
   "/material/:id/media/" :media
   "/taxon/:id/media/" :media
   "/accession/bulk/tags/" :tags
   "/accession/bulk/tags/remove/" :tags
   "/accession/:id/tags/" :tags
   "/accession/:id/tags/:tag-id/" :tags
   "/material/bulk/tags/" :tags
   "/material/bulk/tags/remove/" :tags
   "/material/:id/tags/" :tags
   "/material/:id/tags/:tag-id/" :tags
   "/taxon/bulk/tags/" :tags
   "/taxon/bulk/tags/remove/" :tags
   "/taxon/:id/tags/" :tags
   "/taxon/:id/tags/:tag-id/" :tags})

(def ^:private read-only
  "What a turned-off feature still answers a GET on: a record's page and what
  it needs, so activity can link to it."
  #{"/propagation/:id/" "/propagation/:id/panel/"
    "/media/:id/" "/media/:id/panel/" "/media/:id/transform"
    "/tag/:id/"})

(defn- expected-feature [path]
  (or (some (fn [[prefix feature]] (when (str/starts-with? path prefix) feature)) sections)
      (get elsewhere path)))

(deftest every-feature-route-declares-its-feature
  (let [table (route-table)]
    (is (= (into {} (keep (fn [[path _]] (some->> (expected-feature path) (vector path)))) table)
           (into {} (keep (fn [[path data]] (some->> (:feature data) (vector path)))) table)))))

(deftest only-record-pages-are-read-only
  (is (= read-only
         (into #{} (keep (fn [[path data]] (when (:feature-read-only? data) path))) (route-table)))))
