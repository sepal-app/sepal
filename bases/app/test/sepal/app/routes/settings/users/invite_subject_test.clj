(ns sepal.app.routes.settings.users.invite-subject-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.routes.settings.users.invite :as invite]))

(deftest test-subject-names-the-garden
  (testing "which invitation this is, for someone who keeps records for more
            than one garden"
    (is (= "You have been invited to More Tomorrow Farm on Sepal"
           (invite/invitation-subject "More Tomorrow Farm" nil)))
    (is (= "You have been invited to More Tomorrow Farm on Sepal"
           (invite/invitation-subject "More Tomorrow Farm" "Configured subject"))
        "the garden's own name beats a configured default")))

(deftest test-subject-falls-back
  (testing "a garden that has not named itself still sends a sensible email"
    (is (= "Configured subject" (invite/invitation-subject nil "Configured subject")))
    (is (= "You have been invited to Sepal" (invite/invitation-subject nil nil)))
    (is (= "You have been invited to Sepal" (invite/invitation-subject nil "   ")))))
