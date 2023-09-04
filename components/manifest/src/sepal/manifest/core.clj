(ns sepal.manifest.core
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def digested-file-regex  #"-[a-f\d]{32}")
(def manifest-filename "manifest.edn")

(defn digest-file? [path]
  (and (re-find digested-file-regex (str path))
       (fs/regular-file? path)))

(defn bytes->hex
  "Return the hex values of bytes."
  [b]
  (->> b
       (BigInteger. 1)
       (format "%032X")
       (str/lower-case)))

(defn md5-hex
  "Return the md5 hex of bytes b."
  [b]
  (-> (MessageDigest/getInstance "md5")
      (.digest b)
      (bytes->hex)))

(defn path-md5-hex
  "Return the md5 hex of file at path."
  [path]
  (-> path
      (fs/absolutize)
      (.toUri)
      (io/input-stream)
      (.readAllBytes)
      (md5-hex)))

(defn path->digest-path
  "Return the path name with the files md5 hex digest appended before the file extension.

  e.g. file.js becomes file-<md5 hex>.js
  "
  [path]
  (let [digest (path-md5-hex path)
        filename (.getFileName path)
        parts (-> filename str (str/split #"\."))
        new-filename  (if (= 1 (count parts))
                        (str filename "-" digest)
                        (str (first parts) "-" digest "." (->> parts rest (str/join "."))))]
    (-> path .getParent (.resolve new-filename))))

(defn resource->path
  "Return a java.nio.file.Path to resource."
  [name]
  (some-> name
          (str)
          (io/resource)
          (.toURI)
          (fs/path)))


(defn duplicate-path-with-digest
  "Create a duplicate of a file with the hex digest in the filename.

  Returns the java.nio.file/Path of the new file."
  [path]
  (println (str " duplicate-path-with-digest: " path))
  (let [dest-path (-> path fs/absolutize path->digest-path)]
    (fs/copy path dest-path {:replace-existing true})))

(defn digest-directory
  "Create digest files for the files in directory and return the manifest map."
  [path]
    ;; TODO: Allow passing in a filter function for blacklisting files.
  (let [files (->> (fs/list-dir path)
                   (filter (comp fs/regular-file?))
                   (map str)
                   ;; Don't created a digest file that for digest files
                   (remove digest-file?)
                   ;; Don't created a digest file for the manifest file
                   (remove (partial re-find (re-pattern manifest-filename))))]
    (reduce #(assoc %1
                    (str (fs/relativize path %2))
                    (str (fs/relativize path (duplicate-path-with-digest %2))))
            {}
            files)))

(defn clean
  "Delete the manifest and all of the digest files in directory.

  Returns a sequence of the files that were deleted.
  "
  [directory]
  (when (fs/directory? directory)
    (let [files (fs/list-dir directory)
          manifest-path (fs/path directory manifest-filename)
          removed-files (reduce #(if (digest-file? %2)
                                   (do
                                     (fs/delete %2)
                                     (cons (str %2) %1))
                                   %1)
                                []
                                files)]
      (if (fs/exists? manifest-path)
        (do
          (fs/delete (fs/path directory manifest-filename))
          (cons (str manifest-path) removed-files))
        removed-files))))

(defn write-manifest
  "Create digest files for files in directory and write a manifest.edn.

  Returns a java.nio.file.Path of the manifest file.
  "
  [directory]
  {:pre [(fs/directory? directory)]}
  (let [manifest-path (fs/path directory manifest-filename)
        digest (digest-directory directory)
        out (io/writer (str manifest-path))]
    (pp/pprint digest out)
    manifest-path))

(def cached-manifests (atom {}))

(defn manifest
  "Return the manifest file contents for the resource directory."
  [directory]
  (let [manifest-path (fs/path directory manifest-filename)]
    (if (fs/regular-file? manifest-path)
      (let [last-modified (-> manifest-path fs/file .lastModified)]
        (if (<= last-modified (get-in @cached-manifests [manifest-path :last-modified] 0))
          ;; manifest file hasn't changed since last cached, return the cached data
          (get-in @cached-manifests [manifest-path :data])
          (let [data (-> manifest-path
                         str
                         (io/reader)
                         (java.io.PushbackReader.)
                         (edn/read))]
            ;; update the cache
            (swap! cached-manifests assoc manifest-path {:last-modified last-modified :data data})
            data)))
      (do
        ;; manifest file doesn't exist so reset the cache for that file and return nil
        (swap! cached-manifests assoc manifest-path nil)
        nil))))
