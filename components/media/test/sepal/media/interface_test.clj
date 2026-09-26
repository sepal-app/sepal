(ns sepal.media.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [matcher-combinators.test :refer [match?]]
            [next.jdbc.sql :as jdbc.sql]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.media.interface :as media.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest test-create
  (let [db *db*]
    (tf/testing "create!"
      {[::user.i/factory :key/user] {:db db}}
      (fn [{:keys [user]}]
        (let [data {:s3-bucket "test-bucket"
                    :s3-key "test-key.jpg"
                    :title "Test Image"
                    :description "A test image"
                    :size-in-bytes 1024
                    :media-type "image/jpeg"
                    :created-by (:user/id user)}
              result (media.i/create! db data)]
          (is (match? {:media/id pos-int?
                       :media/s3-bucket "test-bucket"
                       :media/s3-key "test-key.jpg"
                       :media/title "Test Image"
                       :media/description "A test image"
                       :media/size-in-bytes 1024
                       :media/media-type "image/jpeg"
                       :media/created-by (:user/id user)}
                      result))
          ;; Clean up
          (media.i/delete! db (:media/id result)))))))

(deftest test-get-by-id
  (let [db *db*]
    (tf/testing "get-by-id"
      {[::user.i/factory :key/user] {:db db}
       [::media.i/factory :key/media] {:db db :user (ig/ref :key/user)}}

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
      {[::user.i/factory :key/user] {:db db}}
      (fn [{:keys [user]}]
        (let [data {:s3-bucket "test-bucket"
                    :s3-key "delete-test.jpg"
                    :size-in-bytes 512
                    :media-type "image/jpeg"
                    :created-by (:user/id user)}
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
      {[::user.i/factory :key/user] {:db db}
       [::media.i/factory :key/media] {:db db :user (ig/ref :key/user)}}

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
      {[::user.i/factory :key/user] {:db db}
       [::media.i/factory :key/media] {:db db :user (ig/ref :key/user)}}

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
      {[::user.i/factory :key/user] {:db db}
       [::taxon.i/factory :key/taxon] {:db db}
       [::media.i/factory :key/media1] {:db db :user (ig/ref :key/user)}
       [::media.i/factory :key/media2] {:db db :user (ig/ref :key/user)}}

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

(deftest test-get-linked-below
  (let [db *db*]
    (tf/testing "a genus, a species under it, an accession of the genus and its material"
      {[::user.i/factory :key/user] {:db db}
       [::taxon.i/factory :key/genus] {:db db :rank :genus}
       [::accession.i/factory :key/acc] {:db db :taxon (ig/ref :key/genus)}
       [::location.i/factory :key/loc] {:db db}
       [::material.i/factory :key/mat] {:db db
                                        :accession (ig/ref :key/acc)
                                        :location (ig/ref :key/loc)}
       [::media.i/factory :key/on-genus] {:db db :user (ig/ref :key/user)}
       [::media.i/factory :key/on-species] {:db db :user (ig/ref :key/user)}
       [::media.i/factory :key/on-acc] {:db db :user (ig/ref :key/user)}
       [::media.i/factory :key/on-mat] {:db db :user (ig/ref :key/user)}}
      (fn [{:keys [genus acc loc mat on-genus on-species on-acc on-mat]}]
        (let [species (taxon.i/create! db {:name "Belowia alba"
                                           :rank :species
                                           :parent-id (:taxon/id genus)})
              links (fn [resource-type id & opts]
                      (->> (apply media.i/get-linked db resource-type id opts)
                           (map (juxt :media/id (comp :resource-type :media/link)))
                           set))
              all [on-genus on-species on-acc on-mat]]
          (try
            (media.i/link! db (:media/id on-genus) (:taxon/id genus) :taxon)
            (media.i/link! db (:media/id on-species) (:taxon/id species) :taxon)
            (media.i/link! db (:media/id on-acc) (:accession/id acc) :accession)
            (media.i/link! db (:media/id on-mat) (:material/id mat) :material)
            (is (= #{[(:media/id on-genus) "taxon"]}
                   (links "taxon" (:taxon/id genus)))
                "direct is only what is linked to the genus itself")
            (is (= #{[(:media/id on-genus) "taxon"]
                     [(:media/id on-species) "taxon"]
                     [(:media/id on-acc) "accession"]
                     [(:media/id on-mat) "material"]}
                   (links "taxon" (:taxon/id genus) :scope :below))
                "below reaches a child taxon, an accession and its material")
            (is (= #{[(:media/id on-acc) "accession"]
                     [(:media/id on-mat) "material"]}
                   (links "accession" (:accession/id acc) :scope :below)))
            (is (= #{[(:media/id on-mat) "material"]}
                   (links "location" (:location/id loc) :scope :below)))
            (is (= 2 (count (media.i/get-linked db "taxon" (:taxon/id genus)
                                                :scope :below :limit 2)))
                "and it pages")
            (finally
              (doseq [m all] (media.i/unlink! db (:media/id m)))
              (jdbc.sql/delete! db :taxon {:id (:taxon/id species)}))))))))

(deftest test-unlink-resource
  (let [db *db*]
    (tf/testing "unlink-resource!"
      {[::user.i/factory :key/user] {:db db}
       [::taxon.i/factory :key/taxon] {:db db}
       [::media.i/factory :key/media] {:db db :user (ig/ref :key/user)}}
      (fn [{:keys [taxon media]}]
        (media.i/link! db (:media/id media) (:taxon/id taxon) :taxon)
        (media.i/unlink-resource! db :taxon (:taxon/id taxon))
        (is (nil? (media.i/get-link db (:media/id media))))
        (is (some? (media.i/get-by-id db (:media/id media)))
            "The media itself survives; only the link goes")))))
