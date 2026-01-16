(ns sepal.media-transform.interface-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.media-transform.cache :as cache]
            [sepal.media-transform.interface :as media-transform.i])
  (:import [java.awt.image BufferedImage]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [javax.imageio ImageIO]))

;; Test fixtures

(def ^:dynamic *temp-dir* nil)
(def ^:dynamic *cache-ds* nil)

(defn- create-test-image
  "Create a test image file with given dimensions."
  [path width height]
  (let [img (BufferedImage. width height BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)
        f (io/file path)]
    ;; Draw something visible
    (.setColor g java.awt.Color/RED)
    (.fillRect g 0 0 width height)
    (.setColor g java.awt.Color/BLUE)
    (.fillOval g 10 10 (- width 20) (- height 20))
    (.dispose g)
    ;; Ensure parent exists
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (ImageIO/write img "jpg" f)
    f))

(defn temp-dir-fixture [f]
  (let [temp-path (Files/createTempDirectory "sepal-image-test"
                                             (into-array FileAttribute []))
        temp-dir (.toFile temp-path)]
    (try
      (binding [*temp-dir* temp-dir
                *cache-ds* (cache/init-db! (io/file temp-dir "cache.db"))]
        (f))
      (finally
        ;; Cleanup temp dir
        (doseq [file (reverse (file-seq temp-dir))]
          (.delete file))))))

(use-fixtures :each temp-dir-fixture)

;; Tests

(deftest test-cache-key
  (testing "cache-key generates consistent hashes"
    (let [key1 (media-transform.i/cache-key 123 {:width 300 :height 300})
          key2 (media-transform.i/cache-key 123 {:width 300 :height 300})
          key3 (media-transform.i/cache-key 123 {:height 300 :width 300})]  ; Different order
      (is (= 32 (count key1)) "Hash should be 32 chars")
      (is (= key1 key2) "Same inputs should produce same key")
      (is (= key1 key3) "Order of params shouldn't matter")))

  (testing "cache-key produces different hashes for different inputs"
    (let [key1 (media-transform.i/cache-key 123 {:width 300 :height 300})
          key2 (media-transform.i/cache-key 124 {:width 300 :height 300})
          key3 (media-transform.i/cache-key 123 {:width 400 :height 300})]
      (is (not= key1 key2) "Different media-id should produce different key")
      (is (not= key1 key3) "Different params should produce different key"))))

(deftest test-transform-contain
  (testing "transform with contain fit"
    (let [source (create-test-image (io/file *temp-dir* "source.jpg") 800 600)
          target (io/file *temp-dir* "output.jpg")]
      (media-transform.i/transform source target {:width 200 :height 200 :fit :contain})
      (is (.exists target) "Output file should exist")
      (let [img (ImageIO/read target)]
        ;; With contain, the image should fit within 200x200 while maintaining aspect ratio
        ;; 800x600 -> 200x150 (width is limiting factor)
        (is (= 200 (.getWidth img)) "Width should be 200")
        (is (= 150 (.getHeight img)) "Height should maintain aspect ratio")))))

(deftest test-transform-crop
  (testing "transform with crop fit"
    (let [source (create-test-image (io/file *temp-dir* "source.jpg") 800 600)
          target (io/file *temp-dir* "output.jpg")]
      (media-transform.i/transform source target {:width 200 :height 200 :fit :crop})
      (is (.exists target) "Output file should exist")
      (let [img (ImageIO/read target)]
        ;; With crop, the image should be exactly 200x200
        (is (= 200 (.getWidth img)) "Width should be exactly 200")
        (is (= 200 (.getHeight img)) "Height should be exactly 200")))))

(deftest test-transform-format-conversion
  (testing "transform converts PNG to JPG"
    (let [source-png (io/file *temp-dir* "source.png")
          _ (let [img (BufferedImage. 100 100 BufferedImage/TYPE_INT_RGB)]
              (ImageIO/write img "png" source-png))
          target (io/file *temp-dir* "output.jpg")]
      (media-transform.i/transform source-png target {:width 50 :height 50 :format :jpg})
      (is (.exists target) "Output file should exist")
      (is (= 50 (.getWidth (ImageIO/read target)))))))

(deftest test-get-or-transform-cache-miss
  (testing "get-or-transform creates cached file on miss"
    (let [source (create-test-image (io/file *temp-dir* "source.jpg") 400 300)
          result (media-transform.i/get-or-transform *cache-ds* *temp-dir*
                                           1 source
                                           {:width 100 :height 100})]
      (is (false? (:hit? result)) "Should be a cache miss")
      (is (.exists (io/file (:path result))) "Cached file should exist"))))

(deftest test-get-or-transform-cache-hit
  (testing "get-or-transform returns cached file on hit"
    (let [source (create-test-image (io/file *temp-dir* "source.jpg") 400 300)
          opts {:width 100 :height 100}
          result1 (media-transform.i/get-or-transform *cache-ds* *temp-dir* 1 source opts)
          result2 (media-transform.i/get-or-transform *cache-ds* *temp-dir* 1 source opts)]
      (is (false? (:hit? result1)) "First call should be a cache miss")
      (is (true? (:hit? result2)) "Second call should be a cache hit")
      (is (= (:path result1) (:path result2)) "Both should return same path"))))

(deftest test-evict-lru
  (testing "evict-lru removes oldest entries"
    (let [source (create-test-image (io/file *temp-dir* "source.jpg") 400 300)]
      ;; Create several cached transforms
      (doseq [i (range 5)]
        (media-transform.i/get-or-transform *cache-ds* *temp-dir*
                                  i source
                                  {:width (* 100 (inc i)) :height 100})
        ;; Small delay to ensure different timestamps
        (Thread/sleep 10))

      ;; Get total size and set max to half
      (let [total-size (cache/total-size *cache-ds*)
            max-size (/ total-size 2)
            evicted (media-transform.i/evict-lru! *cache-ds* *temp-dir* max-size)]
        (is (pos? evicted) "Should have evicted some entries")
        (is (<= (cache/total-size *cache-ds*) max-size)
            "Cache size should be under max")))))

(deftest test-image-content-type
  (testing "image-content-type? correctly identifies image types"
    (is (true? (media-transform.i/image-content-type? "image/jpeg")))
    (is (true? (media-transform.i/image-content-type? "image/png")))
    (is (true? (media-transform.i/image-content-type? "image/gif")))
    (is (false? (media-transform.i/image-content-type? "application/pdf")))
    (is (false? (media-transform.i/image-content-type? "text/plain")))))
