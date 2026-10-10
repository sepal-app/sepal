(ns sepal.app.routes.settings.features-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [next.jdbc.sql :as jdbc.sql]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.settings.interface :as settings.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(use-fixtures :each
  (fn [t]
    (t)
    (doseq [k ["features.observations" "features.propagation" "features.media" "features.tags"]]
      (settings.i/delete! *db* k))))

(defn- session-as [role]
  (let [email (str (name role) "-" (random-uuid) "@test.com")]
    (user.i/create! *db* {:email email :password "testpassword123" :role role})
    (app.test/login email "testpassword123")))

(defn- post-features [params]
  (let [sess (session-as :admin)
        {:keys [response] :as sess} (peri/request sess "/settings/features")
        token (test.i/response-anti-forgery-token response)]
    (peri/request sess "/settings/features"
                  :request-method :post
                  :params (merge {:__anti-forgery-token token} params))))

(deftest test-every-feature-starts-on
  (let [{:keys [response]} (peri/request (session-as :admin) "/settings/features")
        body (Jsoup/parse ^String (:body response))]
    (is (= 200 (:status response)))
    (doseq [field ["observations" "propagation" "media" "tags"]]
      (is (some? (.selectFirst body (str "input#" field "[checked]"))) field))))

(deftest test-turning-features-off
  (testing "an unticked box saves off, a ticked one on, and the change is recorded"
    (let [{:keys [response] :as sess} (post-features {:observations "1" :media "1"})]
      (is (= 303 (:status response)))
      (is (= {"features.observations" "on"
              "features.propagation" "off"
              "features.media" "on"
              "features.tags" "off"}
             (settings.i/get-values *db* "features")))
      (let [{:keys [response]} (peri/follow-redirect sess)
            body (Jsoup/parse ^String (:body response))]
        (is (nil? (.selectFirst body "input#propagation[checked]")))
        (is (some? (.selectFirst body "input#media[checked]"))))
      (is (seq (jdbc.sql/find-by-keys *db* :activity {:type "settings/updated"}))))))

(deftest test-an-editor-is-refused
  (is (= 403 (:status (:response (peri/request (session-as :editor) "/settings/features"))))))
