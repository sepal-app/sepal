(ns sepal.app.ui.location-path-test
  (:require [clojure.test :refer [deftest is]]
            [sepal.app.ui.location-path :as location-path]))

(deftest test-text
  (is (= "Zone › Orchard › Row 3"
         (location-path/text [{:location/name "Zone"}
                              {:location/name "Orchard"}
                              {:location/name "Row 3"}])))
  (is (= "Nursery" (location-path/text [{:location/name "Nursery"}]))))
