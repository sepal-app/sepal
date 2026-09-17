(ns sepal.app.routes.taxon.form-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [malli.generator :as mg]
            [matcher-combinators.test :refer [match?]]
            [sepal.app.params :as params]
            [sepal.app.routes.taxon.form :as taxon.form]))

(deftest form-decode
  (testing "decode vernacular names"
    (let [{:keys [vernacular-names]
           :as form-data} (mg/generate taxon.form/FormParams)
          ;; vernacular names are posted with the keys in separate form data
          ;; fields
          form-data (-> form-data
                        (assoc :vernacular-name-id (mapv :id vernacular-names)
                               :vernacular-name-name (mapv :name vernacular-names)
                               :vernacular-name-language (mapv :language vernacular-names))
                        (dissoc :vernacular-names))
          form-params  (params/decode taxon.form/FormParams form-data)]
      (is (match? {:vernacular-names vernacular-names}
                  form-params)))))

(deftest parentage-decode
  (testing "indexed slots collect into one ordered list"
    (let [decoded (params/decode taxon.form/FormParams
                                 {:name "Acer × freemanii"
                                  :author ""
                                  :rank "species"
                                  :vernacular-name-name []
                                  :vernacular-name-language []
                                  :parentage-parent-0 "11"
                                  :parentage-role-0 "seed"
                                  :parentage-parent-1 "22"
                                  :parentage-role-1 "pollen"})]
      (is (= [{:parent-taxon-id "11" :role "seed"}
              {:parent-taxon-id "22" :role "pollen"}]
             (:parentage decoded)))
      (testing "and the raw slot keys do not survive into the payload"
        (is (empty? (filter #(str/starts-with? (name %) "parentage-")
                            (keys decoded)))))))

  (testing "an unfilled slot is dropped, not rejected -- the form always offers
            a spare and an unused spare is not an error"
    (is (= [{:parent-taxon-id "11" :role "unknown"}]
           (:parentage (params/decode taxon.form/FormParams
                                      {:name "Acer × freemanii"
                                       :author ""
                                       :rank "species"
                                       :vernacular-name-name []
                                       :vernacular-name-language []
                                       :parentage-parent-0 "11"
                                       :parentage-role-0 ""
                                       :parentage-parent-1 ""
                                       :parentage-role-1 ""})))))

  (testing "order comes from the index, not from the map"
    (is (= ["1" "2" "3"]
           (mapv :parent-taxon-id
                 (:parentage (params/decode taxon.form/FormParams
                                            {:name "× Potinara"
                                             :author ""
                                             :rank "genus"
                                             :vernacular-name-name []
                                             :vernacular-name-language []
                                             :parentage-parent-2 "3"
                                             :parentage-parent-0 "1"
                                             :parentage-parent-1 "2"})))))))
