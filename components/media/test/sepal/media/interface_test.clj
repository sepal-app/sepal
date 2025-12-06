(ns sepal.media.interface-test
  (:require [clojure.test :as test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.media.interface :as media.i]
            [sepal.taxon.interface :as taxon.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (let [db *db*]
    (tf/testing "create!"
      {}
      (fn [_]
        (let [data {:s3-bucket "test-bucket"
                    :s3-key "test-key.jpg"
                    :title "Test Image"
                    :description "A test image"
                    :size-in-bytes 1024
                    :media-type "image/jpeg"}
              result (media.i/create! db data)]
          (is (match? {:media/id pos-int?
                       :media/s3-bucket "test-bucket"
                       :media/s3-key "test-key.jpg"
                       :media/title "Test Image"
                       :media/description "A test image"
                       :media/size-in-bytes 1024
                       :media/media-type "image/jpeg"}
                      result))
          ;; Clean up
          (media.i/delete! db (:media/id result)))))))

(deftest test-get-by-id
  (let [db *db*]
    (tf/testing "get-by-id"
      {[::media.i/factory :key/media]
       {:db db}}

      (fn [{:keys [media]}]
        (testing "returns media when found"
          (let [result (media.i/get-by-id db (:media/id media))]
            (is (match? {:media/id (:media/id media)
                         :media/s3-bucket string?
                         :media/s3-key string?
                         :media/size-in-bytes pos-int?
                         :media/media-type string?}
                        result))))

        (testing "returns nil when not found"
          (let [result (media.i/get-by-id db 999999)]
            (is (nil? result))))))))

(deftest test-delete
  (let [db *db*]
    (tf/testing "delete!"
      {}
      (fn [_]
        (let [data {:s3-bucket "test-bucket"
                    :s3-key "delete-test.jpg"
                    :size-in-bytes 512
                    :media-type "image/jpeg"}
              media (media.i/create! db data)
              media-id (:media/id media)]
          ;; Verify it exists
          (is (some? (media.i/get-by-id db media-id)))
          ;; Delete it
          (media.i/delete! db media-id)
          ;; Verify it's gone
          (is (nil? (media.i/get-by-id db media-id))))))))

(deftest test-link-and-unlink
  (let [db *db*]
    (tf/testing "link! and unlink!"
      {[::media.i/factory :key/media]
       {:db db}}

      (fn [{:keys [media]}]
        (testing "link! creates a media link"
          (let [result (media.i/link! db (:media/id media) 1 :taxon)]
            (is (match? {:media-link/media-id (:media/id media)
                         :media-link/resource-id 1
                         :media-link/resource-type "taxon"}
                        result))))

        (testing "unlink! removes the media link"
          (is (match? {:next.jdbc/update-count 1}
                      (media.i/unlink! db (:media/id media)))))))))

(deftest test-get-link
  (let [db *db*]
    (tf/testing "get-link"
      {[::media.i/factory :key/media]
       {:db db}}

      (fn [{:keys [media]}]
        (testing "returns nil when no link exists"
          (is (nil? (media.i/get-link db (:media/id media)))))

        (testing "returns link when it exists"
          (media.i/link! db (:media/id media) 42 :accession)
          (let [result (media.i/get-link db (:media/id media))]
            (is (match? {:media-link/media-id (:media/id media)
                         :media-link/resource-id 42
                         :media-link/resource-type "accession"}
                        result)))
          ;; Clean up
          (media.i/unlink! db (:media/id media)))))))

(deftest test-get-linked
  (let [db *db*]
    (tf/testing "get-linked"
      {[::taxon.i/factory :key/taxon]
       {:db db}

       [::media.i/factory :key/media1]
       {:db db}

       [::media.i/factory :key/media2]
       {:db db}}

      (fn [{:keys [taxon media1 media2]}]
        (let [taxon-id (:taxon/id taxon)]
          (testing "returns empty vector when no media linked"
            (let [result (media.i/get-linked db "taxon" taxon-id)]
              (is (= [] result))))

          (testing "returns linked media"
            ;; Link both media to the taxon
            (media.i/link! db (:media/id media1) taxon-id :taxon)
            (media.i/link! db (:media/id media2) taxon-id :taxon)

            (let [result (media.i/get-linked db "taxon" taxon-id)]
              (is (= 2 (count result)))
              (is (match? [{:media/id (:media/id media1)}
                           {:media/id (:media/id media2)}]
                          (sort-by :media/id result)))))

          ;; Clean up
          (media.i/unlink! db (:media/id media1))
          (media.i/unlink! db (:media/id media2)))))))
