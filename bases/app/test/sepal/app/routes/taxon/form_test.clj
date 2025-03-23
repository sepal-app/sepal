(ns sepal.app.routes.taxon.form-test
  (:require [clojure.test :refer :all]
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
