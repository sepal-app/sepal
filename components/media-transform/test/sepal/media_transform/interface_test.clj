(ns sepal.media-transform.interface-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.media-transform.cache :as cache]
            [sepal.media-transform.core :as core]
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
                                                     1 "jpg" (constantly source)
                                                     {:width 100 :height 100})]
      (is (false? (:hit? result)) "Should be a cache miss")
      (is (.exists (io/file (:path result))) "Cached file should exist"))))

(deftest test-get-or-transform-cache-hit
  (testing "get-or-transform returns cached file on hit"
    (let [source (create-test-image (io/file *temp-dir* "source.jpg") 400 300)
          opts {:width 100 :height 100}
          fetches (atom 0)
          fetch (fn [] (swap! fetches inc) source)
          result1 (media-transform.i/get-or-transform *cache-ds* *temp-dir* 1 "jpg" fetch opts)
          result2 (media-transform.i/get-or-transform *cache-ds* *temp-dir* 1 "jpg" fetch opts)]
      (is (false? (:hit? result1)) "First call should be a cache miss")
      (is (true? (:hit? result2)) "Second call should be a cache hit")
      (is (= 1 @fetches) "a hit does not fetch the original again")
      (is (= (:path result1) (:path result2)) "Both should return same path"))))

(deftest test-evict-lru
  (testing "evict-lru removes oldest entries"
    (let [source (create-test-image (io/file *temp-dir* "source.jpg") 400 300)]
      ;; Create several cached transforms
      (doseq [i (range 5)]
        (media-transform.i/get-or-transform *cache-ds* *temp-dir*
                                            i "jpg" (constantly source)
                                            {:width (* 100 (inc i)) :height 100})
        ;; Small delay to ensure different timestamps
        (Thread/sleep 10))

      ;; Get total size and set max to half
      (let [total-size (cache/total-size *cache-ds*)
            max-size (/ total-size 2)
            evicted (media-transform.i/evict-lru! *cache-ds* *temp-dir* max-size)]
        (is (pos? evicted) "Should have evicted some entries")
        (is (<= (cache/total-size *cache-ds*) max-size)
            "Cache size should be under max")
        (is (some? (cache/get-entry *cache-ds* (cache/cache-key 4 {:width 500 :height 100})))
            "it stops once the cache fits, so the newest entry is kept")))))

(deftest test-previewable
  (testing "by media type"
    (is (true? (media-transform.i/previewable? "image/jpeg" "media/a.jpg")))
    (is (true? (media-transform.i/previewable? "image/png" "media/a.png")))
    (is (true? (media-transform.i/previewable? "image/gif" "media/a.gif")))
    (is (true? (media-transform.i/previewable? "image/vnd.adobe.photoshop" "media/a.psd")))
    (is (true? (media-transform.i/previewable? "image/tiff" "media/a.tif"))))
  (testing "by extension, since browsers disagree on a PSD's type"
    (is (true? (media-transform.i/previewable? "application/octet-stream" "media/a.PSD")))
    (is (true? (media-transform.i/previewable? "" "media/a.tiff"))))
  (testing "anything else"
    (is (false? (media-transform.i/previewable? "application/pdf" "media/a.pdf")))
    (is (false? (media-transform.i/previewable? "image/heic" "media/a.heic")))
    (is (false? (media-transform.i/previewable? "text/plain" "media/a.txt")))
    (is (false? (media-transform.i/previewable? nil nil)))))

(defn- fixture [name]
  (io/file (io/resource (str "sepal/media_transform/fixtures/" name))))

(defn- format-name [^java.io.File f]
  (with-open [iis (ImageIO/createImageInputStream f)]
    (.getFormatName ^javax.imageio.ImageReader (.next (ImageIO/getImageReaders iis)))))

(deftest test-psd-and-tiff-are-served-as-jpeg
  (doseq [[name ext] [["rgb.psd" "psd"] ["alpha.psd" "psd"] ["rgb.tif" "tif"] ["cmyk.tif" "tif"]]]
    (testing name
      (let [{:keys [path]} (media-transform.i/get-or-transform *cache-ds* *temp-dir* name ext
                                                               (constantly (fixture name))
                                                               {:width 32 :height 32})
            out (io/file path)]
        (is (str/ends-with? path ".jpg") "a browser can't show the original's format")
        (is (.exists out))
        (is (= "JPEG" (format-name out)))
        (let [img (ImageIO/read out)]
          (is (= [32 24] [(.getWidth img) (.getHeight img)])))))))

(deftest test-transparency-is-flattened-onto-white
  (let [{:keys [path]} (media-transform.i/get-or-transform *cache-ds* *temp-dir* 1 "psd"
                                                           (constantly (fixture "alpha.psd"))
                                                           {:width 64 :height 48})
        img (ImageIO/read (io/file path))
        corner (java.awt.Color. (.getRGB img 1 1))
        middle (java.awt.Color. (.getRGB img 32 24))]
    (is (every? #(> % 240) [(.getRed corner) (.getGreen corner) (.getBlue corner)])
        "the transparent corner is white, not black")
    (is (> (.getRed middle) 150) "the opaque red square is still red")
    (is (< (.getGreen middle) 80))))

(deftest test-cmyk-keeps-its-colour
  (let [{:keys [path]} (media-transform.i/get-or-transform *cache-ds* *temp-dir* 1 "tif"
                                                           (constantly (fixture "cmyk.tif"))
                                                           {:width 64 :height 48})
        c (java.awt.Color. (.getRGB (ImageIO/read (io/file path)) 32 24))]
    (is (> (.getGreen c) (+ 60 (.getRed c))) "green, not inverted or grey")
    (is (> (.getGreen c) (+ 60 (.getBlue c))))))

(deftest test-an-image-over-the-pixel-cap-is-not-decoded
  (let [source (create-test-image (io/file *temp-dir* "big.jpg") 400 300)]
    (with-redefs [core/max-pixels (* 399 300)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"too many pixels"
                            (media-transform.i/transform source (io/file *temp-dir* "out.jpg")
                                                         {:width 100 :height 100}))))
    (with-redefs [core/max-pixels (* 400 300)]
      (is (some? (media-transform.i/transform source (io/file *temp-dir* "out.jpg")
                                              {:width 100 :height 100}))
          "at the cap is fine"))))

(deftest test-a-large-image-is-scaled-exactly
  (testing "a source read at reduced resolution still comes out at the requested size"
    (let [source (create-test-image (io/file *temp-dir* "large.jpg") 3000 2000)
          target (io/file *temp-dir* "small.jpg")]
      (media-transform.i/transform source target {:width 120 :height 120 :fit :crop})
      (let [img (ImageIO/read target)]
        (is (= [120 120] [(.getWidth img) (.getHeight img)])))
      (media-transform.i/transform source target {:width 120 :height 120})
      (let [img (ImageIO/read target)]
        (is (= [120 80] [(.getWidth img) (.getHeight img)]))))))

(defn- with-exif-orientation
  "Write `f`'s JPEG back with an EXIF APP1 segment holding only `orientation`,
  placed after the JFIF segment the way a camera's file has it."
  [f orientation]
  (let [jpeg (Files/readAllBytes (.toPath (io/file f)))
        app0-end (+ 4 (bit-or (bit-shift-left (bit-and (aget jpeg 4) 0xff) 8)
                              (bit-and (aget jpeg 5) 0xff)))
        app1 (byte-array (map unchecked-byte
                              [0xFF 0xE1 0 34
                               0x45 0x78 0x69 0x66 0 0 ; Exif
                               0x4D 0x4D 0 0x2A 0 0 0 8 ; big-endian TIFF header
                               0 1 ; one IFD entry
                               0x01 0x12 0 3 0 0 0 1 0 orientation 0 0
                               0 0 0 0]))]
    (with-open [out (io/output-stream f)]
      (.write out jpeg 0 app0-end)
      (.write out app1)
      (.write out jpeg app0-end (- (alength jpeg) app0-end)))
    f))

(defn- half-and-half
  "A 40x20 JPEG, red on the left and blue on the right."
  [path]
  (let [img (BufferedImage. 40 20 BufferedImage/TYPE_INT_RGB)
        g (.createGraphics img)]
    (.setColor g java.awt.Color/RED)
    (.fillRect g 0 0 20 20)
    (.setColor g java.awt.Color/BLUE)
    (.fillRect g 20 0 20 20)
    (.dispose g)
    (ImageIO/write img "jpg" (io/file path))
    (io/file path)))

(defn- colour-at [^BufferedImage img x y]
  (let [c (java.awt.Color. (.getRGB img x y))]
    (if (> (.getRed c) (.getBlue c)) :red :blue)))

(deftest test-exif-orientation-is-applied
  (doseq [[orientation [w h] corners]
          [[nil [40 20] {:top-left :red :bottom-right :blue}]
           [1 [40 20] {:top-left :red :bottom-right :blue}]
           [3 [40 20] {:top-left :blue :bottom-right :red}]
           [6 [20 40] {:top-left :red :bottom-right :blue}]
           [8 [20 40] {:top-left :blue :bottom-right :red}]]]
    (testing (str "orientation " (or orientation "absent"))
      (let [source (cond-> (half-and-half (io/file *temp-dir* "source.jpg"))
                     orientation (with-exif-orientation orientation))
            target (io/file *temp-dir* "out.jpg")]
        (media-transform.i/transform source target {:width 40 :height 40})
        (let [img (ImageIO/read target)]
          (is (= [w h] [(.getWidth img) (.getHeight img)]))
          (is (= corners {:top-left (colour-at img 3 3)
                          :bottom-right (colour-at img (- w 4) (- h 4))})))))))

(deftest test-a-turned-image-is-subsampled-for-the-turned-box
  (testing "a quarter turn swaps which side the box constrains"
    (let [source (with-exif-orientation (create-test-image (io/file *temp-dir* "wide.jpg") 4000 1000) 6)
          img (#'core/read-image source 1000 100)]
      (is (= [1000 4000] [(.getWidth img) (.getHeight img)])
          "shown 1000 wide, a 1000x100 crop needs every column, so nothing is skipped"))))
