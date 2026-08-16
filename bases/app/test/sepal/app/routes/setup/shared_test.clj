(ns sepal.app.routes.setup.shared-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.mail.interface.protocols :as mail.p]))

(use-fixtures :once default-system-fixture)

(defn- stub-mail []
  (reify mail.p/MailClient
    (send-message [_ _] {:status :sent})))

(deftest test-checks-read-the-context-not-the-environment
  (testing "a hosted process serves many gardens from one environment, so a
            check that reads System/getenv gives every garden the same wrong
            answer"
    (let [checks (setup.shared/check-server-config
                   {:db *db*
                    :mail (stub-mail)
                    :s3-client :a-client
                    :media-upload-bucket "garden-media"
                    :app-domain "garden.example.org"})]
      (is (= :ok (get-in checks [:smtp :status])))
      (is (= :ok (get-in checks [:s3 :status])))
      (is (= :ok (get-in checks [:app-domain :status]))))))

(deftest test-checks-warn-on-what-the-context-lacks
  (testing "an instance with no mail client, no bucket and no domain says so"
    (let [checks (setup.shared/check-server-config {:db *db*})]
      (is (= :warning (get-in checks [:smtp :status])))
      (is (= :warning (get-in checks [:s3 :status])))
      (is (= :warning (get-in checks [:app-domain :status]))))))

(deftest test-s3-needs-both-a-client-and-a-bucket
  (testing "a client with no bucket cannot store anything"
    (let [checks (setup.shared/check-server-config {:db *db* :s3-client :a-client})]
      (is (= :warning (get-in checks [:s3 :status]))))))
