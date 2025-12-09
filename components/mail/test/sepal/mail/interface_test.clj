(ns sepal.mail.interface-test
  (:require [clojure.test :refer [deftest is testing]]
            [integrant.core :as ig]
            [sepal.mail.core :as core]
            [sepal.mail.interface :as mail.i]
            [sepal.mail.interface.protocols :as mail.p]))

(deftest test-create-session
  (testing "creates session with starttls"
    (let [session (core/create-session {:host "smtp.example.com"
                                        :port 587
                                        :username "user"
                                        :password "pass"
                                        :auth true
                                        :tls "starttls"})]
      (is (some? session))
      (is (= "smtp.example.com" (.getProperty session "mail.smtp.host")))
      (is (= "587" (.getProperty session "mail.smtp.port")))
      (is (= "true" (.getProperty session "mail.smtp.auth")))
      (is (= "true" (.getProperty session "mail.smtp.starttls.enable")))))

  (testing "creates session with ssl"
    (let [session (core/create-session {:host "smtp.example.com"
                                        :port 465
                                        :username "user"
                                        :password "pass"
                                        :auth true
                                        :tls "ssl"})]
      (is (some? session))
      (is (= "true" (.getProperty session "mail.smtp.ssl.enable")))))

  (testing "creates session with no tls"
    (let [session (core/create-session {:host "smtp.example.com"
                                        :port 25
                                        :auth false
                                        :tls "none"})]
      (is (some? session))
      (is (nil? (.getProperty session "mail.smtp.starttls.enable")))
      (is (nil? (.getProperty session "mail.smtp.ssl.enable"))))))

(deftest test-integrant-init
  (testing "creates client via integrant"
    (let [client (ig/init-key ::mail.i/client {:host "smtp.example.com"
                                               :port 587
                                               :username "user"
                                               :password "pass"
                                               :auth true
                                               :tls "starttls"})]
      (is (some? client))
      (is (satisfies? mail.p/MailClient client)))))
