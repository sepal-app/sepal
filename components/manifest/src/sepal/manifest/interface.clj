(ns sepal.manifest.interface
  (:require [sepal.manifest.core :as core]))

(defn write-manifest
  "Create digest files for files in directory and write a manifest.edn file.

  Returns a java.nio.file.Path of the manifest file.
  "
  [directory]
  (core/write-manifest directory))

(defn clean
  "Delete the manifest and all of the digest files in directory."
  [directory]
  (core/clean directory))

(defn manifest
  "Return the manifest file contents for directory."
  [directory]
  (core/manifest directory))

(defn resource->path
  "Return a java.nio.file.Path to resource."
  [resource]
  (core/resource->path resource))

(defn cli
  "Helper to work with static assets from the command line. "
  [{:keys [command args]}]

  (case command
    :build
    (doseq [path args]
      (if-let [directory (resource->path path)]
        (let [asset-file (write-manifest directory)]
          (println "Created" (str asset-file)))
        (println (str "WARNING: Could not get the asset directory: " path))))

    :clean
    (doseq [path args]
      (let [directory (resource->path path)
            removed-files (clean directory)]
        (doseq [f removed-files]
          (println "Removed" f))))))

(comment
  ;; Create gzip, digest files and a manifest file for directory
  (write-manifest (resource->path "app/static/js"))
  ;; Get the manifest file for resource
  (manifest (resource->path "app/static/js"))
  ;; Remove the gzipped, digest files and manifest file for directory
  (clean (resource->path "app/static/js"))
  ())
