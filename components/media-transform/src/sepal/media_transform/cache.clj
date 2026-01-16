(ns sepal.media-transform.cache
  "LRU cache management for transformed images using a separate SQLite database."
  (:require [clojure.java.io :as io]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql :as jdbc.sql])
  (:import [java.security MessageDigest]
           [java.time Instant]))

(def ^:private schema
  "CREATE TABLE IF NOT EXISTS image_cache (
     hash TEXT PRIMARY KEY,
     media_id INTEGER NOT NULL,
     size_bytes INTEGER NOT NULL,
     accessed_at TEXT NOT NULL
   );
   CREATE INDEX IF NOT EXISTS idx_image_cache_accessed ON image_cache(accessed_at);
   CREATE INDEX IF NOT EXISTS idx_image_cache_media ON image_cache(media_id);")

(defn- bytes->hex
  "Convert byte array to hex string."
  [^bytes bs]
  (let [sb (StringBuilder.)]
    (doseq [b bs]
      (.append sb (format "%02x" b)))
    (.toString sb)))

(defn cache-key
  "Generate a cache key from media-id and transform params.
   Returns a SHA-256 hash (first 32 chars)."
  [media-id params]
  (let [input (pr-str [media-id (sort params)])
        digest (MessageDigest/getInstance "SHA-256")
        hash-bytes (.digest digest (.getBytes input "UTF-8"))]
    (subs (bytes->hex hash-bytes) 0 32)))

(defn cache-path
  "Generate the file path for a cached image.
   Uses first 2 chars of hash as subdirectory for better filesystem performance."
  [cache-dir hash ext]
  (let [subdir (subs hash 0 2)]
    (io/file cache-dir "images" subdir (str hash "." ext))))

(defn init-db!
  "Initialize the cache database. Creates tables if they don't exist."
  [db-path]
  (let [db-file (io/file db-path)]
    ;; Ensure parent directory exists
    (when-let [parent (.getParentFile db-file)]
      (.mkdirs parent))
    (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname (str db-file)})]
      ;; Run schema
      (jdbc/execute! ds [schema])
      ds)))

(defn get-entry
  "Get a cache entry by hash. Returns nil if not found."
  [ds hash]
  (jdbc.sql/get-by-id ds :image_cache hash :hash
                      {:builder-fn rs/as-unqualified-kebab-maps}))

(defn touch!
  "Update the accessed_at timestamp for a cache entry."
  [ds hash]
  (jdbc.sql/update! ds :image_cache
                    {:accessed_at (str (Instant/now))}
                    {:hash hash}))

(defn put!
  "Add or update a cache entry."
  [ds {:keys [hash media-id size-bytes]}]
  (let [now (str (Instant/now))]
    (jdbc/execute! ds
                   ["INSERT INTO image_cache (hash, media_id, size_bytes, accessed_at)
                     VALUES (?, ?, ?, ?)
                     ON CONFLICT(hash) DO UPDATE SET accessed_at = ?"
                    hash media-id size-bytes now now])))

(defn delete!
  "Delete a cache entry by hash."
  [ds hash]
  (jdbc.sql/delete! ds :image_cache {:hash hash}))

(defn total-size
  "Get total size of all cached images in bytes."
  [ds]
  (-> (jdbc/execute-one! ds ["SELECT COALESCE(SUM(size_bytes), 0) as total FROM image_cache"])
      :total))

(defn oldest-entries
  "Get the oldest cache entries, ordered by accessed_at ascending."
  [ds limit]
  (jdbc/execute! ds
                 ["SELECT hash, size_bytes FROM image_cache ORDER BY accessed_at ASC LIMIT ?"
                  limit]
                 {:builder-fn rs/as-unqualified-kebab-maps}))

(defn evict-lru!
  "Evict oldest entries until cache is under max-size-bytes.
   Returns number of entries evicted."
  [ds cache-dir max-size-bytes]
  (loop [evicted 0]
    (let [current-size (total-size ds)]
      (if (<= current-size max-size-bytes)
        evicted
        ;; Get batch of oldest entries
        (let [entries (oldest-entries ds 100)]
          (if (empty? entries)
            evicted
            (do
              (doseq [{:keys [hash]} entries]
                ;; Delete file (try both extensions)
                (doseq [ext ["jpg" "png" "gif" "bmp"]]
                  (let [f (cache-path cache-dir hash ext)]
                    (when (.exists f)
                      (.delete f))))
                ;; Delete db entry
                (delete! ds hash))
              (recur (+ evicted (count entries))))))))))

(defn clear-all!
  "Clear all cache entries and files."
  [ds cache-dir]
  (jdbc/execute! ds ["DELETE FROM image_cache"])
  (let [images-dir (io/file cache-dir "images")]
    (when (.exists images-dir)
      (doseq [f (file-seq images-dir)
              :when (.isFile f)]
        (.delete f)))))
