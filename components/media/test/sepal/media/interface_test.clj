(ns sepal.media.interface-test
  (:require [clojure.test :as test :refer :all]
            [integrant.core :as ig]
            [matcher-combinators.test :refer [match?]]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db*
                                           default-system-fixture]]
            [sepal.media.interface :as media.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(deftest media-link
  (let [db *db*]
    (tf/testing "link!"
      {[::user.i/factory :key/user]
       {:db db}

       [::media.i/factory :key/media]
       {:db db
        :created-by (ig/ref :key/user)}}

      (fn [{:keys [media]}]
        (let [result (media.i/link! db (:media/id media) 1 :taxon)]
          (is (match? {:media-link/media-id (:media/id media)
                       :media-link/resource-id 1
                       :media-link/resource-type "taxon"}
                      result))
          (is (match? {:next.jdbc/update-count 1}
                      (media.i/unlink! db (:media/id media)))))))))
