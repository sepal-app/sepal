(ns sepal.media-transform.interface
  "Public interface for image transformation and caching."
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [sepal.media-transform.cache :as cache]
            [sepal.media-transform.core :as core]))

(defn transform
  "Transform an image file and save to target path.
   
   Options:
   - :width    - Target width
   - :height   - Target height
   - :fit      - :crop or :contain (default :contain)
   - :quality  - JPEG quality 1-100 (default 85)
   - :format   - :jpg, :png, or :original (default :original)
   
   Returns the target path on success."
  [source-path target-path opts]
  (core/transform source-path target-path opts))

(defn cache-key
  "Generate a cache key from media-id and transform params."
  [media-id params]
  (cache/cache-key media-id params))

(defn get-or-transform
  "Get cached transform or generate and cache it.
   
   Args:
   - cache-ds   - Cache database datasource
   - cache-dir  - Cache directory path
   - media-id   - Media ID
   - source-path - Path to source image
   - opts       - Transform options (same as transform)
   
   Returns {:path \"...\" :hit? true/false}"
  [cache-ds cache-dir media-id source-path opts]
  (let [hash (cache/cache-key media-id opts)
        ;; Determine output extension
        out-format (or (:format opts) :original)
        ext (if (= out-format :original)
              (-> source-path str (subs (inc (.lastIndexOf (str source-path) "."))))
              (name out-format))
        cached-path (cache/cache-path cache-dir hash ext)]

    ;; Check if cached version exists
    (if (and (.exists (io/file cached-path))
             (cache/get-entry cache-ds hash))
      ;; Cache hit - update access time
      (do
        (cache/touch! cache-ds hash)
        {:path (str cached-path) :hit? true})

      ;; Cache miss - generate
      (do
        (core/transform source-path cached-path opts)
        (let [size (.length (io/file cached-path))]
          (cache/put! cache-ds {:hash hash
                                :media-id media-id
                                :size-bytes size}))
        {:path (str cached-path) :hit? false}))))

(defn evict-lru!
  "Evict oldest entries until cache is under max-size-bytes.
   Returns number of entries evicted."
  [cache-ds cache-dir max-size-bytes]
  (cache/evict-lru! cache-ds cache-dir max-size-bytes))

(defn image-content-type?
  "Returns true if the content type is a supported image format."
  [content-type]
  (core/image-content-type? content-type))

(defn init-cache-db!
  "Initialize the cache database. Returns datasource."
  [db-path]
  (cache/init-db! db-path))

;; Integrant lifecycle

(defmethod ig/init-key ::service
  [_ {:keys [cache-dir max-cache-size-mb]
      :or {max-cache-size-mb 500}}]
  (let [cache-db-path (io/file cache-dir "cache.db")
        cache-ds (cache/init-db! cache-db-path)
        max-bytes (* max-cache-size-mb 1024 1024)]
    {:cache-ds cache-ds
     :cache-dir cache-dir
     :max-cache-size-bytes max-bytes}))

(defmethod ig/halt-key! ::service
  [_ _service]
  ;; Nothing special to do - datasource will be GC'd
  nil)
