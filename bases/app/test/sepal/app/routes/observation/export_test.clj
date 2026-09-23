(ns sepal.app.routes.observation.export-test
  (:require [clojure.data.csv :as data.csv]
            [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.accession.interface :as accession.i]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.location.interface :as location.i]
            [sepal.material.interface :as material.i]
            [sepal.observation.interface :as observation.i]
            [sepal.taxon.interface :as taxon.i]
            [sepal.user.interface :as user.i]))

(use-fixtures :once default-system-fixture)

(def ^:private password "testpassword123")

;; A function, not a top-level def: *db* is bound by the fixture at run time,
;; so a def's value expression would capture nil at namespace load.
(defn- fixtures []
  {[::user.i/factory :key/user] {:db *db* :password password :role :editor}
   [::taxon.i/factory :key/taxon] {:db *db*}
   [::location.i/factory :key/location] {:db *db*}
   [::accession.i/factory :key/accession] {:db *db* :taxon (ig/ref :key/taxon)}
   [::material.i/factory :key/material] {:db *db*
                                         :accession (ig/ref :key/accession)
                                         :location (ig/ref :key/location)}})

(defn- create! [& {:keys [user] :as data}]
  (observation.i/create! *db* (-> data
                                  (dissoc :user)
                                  (assoc :created-by (:user/id user)))))

(deftest test-exporting-a-filtered-query-carries-the-right-rows-and-columns
  (tf/testing "GET /observation/export/?q=... the way a client will"
    (fixtures)
    (fn [{:keys [user material accession]}]
      (let [matching (create! :resource-type :material
                              :resource-id (:material/id material)
                              :user user
                              :type "phenology"
                              :value "flowering"
                              :observed-on "2026-03-05"
                              :observed-by "A volunteer")
            decoy (create! :resource-type :material
                           :resource-id (:material/id material)
                           :user user
                           :type "condition"
                           :value "fair"
                           :observed-on "2026-03-05")
            sess (app.test/login (:user/email user) password)
            {:keys [response]} (peri/request sess "/observation/export/"
                                             :params {"q" "type:phenology value:flowering"})
            [header & rows] (data.csv/read-csv (:body response))
            cells (zipmap header (first rows))]
        (is (= 200 (:status response)))
        (is (str/includes? (get-in response [:headers "Content-Type"]) "text/csv"))
        (is (str/includes? (get-in response [:headers "Content-Disposition"]) "attachment"))
        (is (str/includes? (get-in response [:headers "Content-Disposition"]) "observations-"))

        (is (= 1 (count rows)) "only the matching row, not the decoy")
        (is (= (str (:observation/id matching)) (get cells "observation_id")))
        (is (= "phenology" (get cells "observation_type")))
        (is (= "flowering" (get cells "observation_value")))
        (is (= "A volunteer" (get cells "observed_by")))
        (is (= "material" (get cells "subject_type")))
        (is (= (:material/code material) (get cells "material_code")))
        (is (= (:accession/code accession) (get cells "accession_code")))

        (doseq [o [matching decoy]]
          (observation.i/delete! *db* (:observation/id o)))))))
