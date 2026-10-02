(ns sepal.app.routes.media.s3-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.app.routes.media.s3 :as s3]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.aws-s3.interface :as aws-s3.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(use-fixtures :once default-system-fixture)

(def ^:private file
  "{\"filename\":\"rose.jpg\",\"contentType\":\"image/jpeg\",\"size\":1,\"id\":\"uppy-rose\"}")

(deftest test-signing-is-refused-when-uploads-are-off
  (tf/testing "with no presigner the route answers 404 rather than signing"
    {[::user.i/factory :key/user] {:db *db* :password "testpassword123" :role :editor}}
    (fn [{:keys [user]}]
      (let [sess (app.test/login (:user/email user) "testpassword123")
            ;; Any page with a form carries the token; the uploader sends it
            ;; as a header.
            {:keys [response] :as sess} (peri/request sess "/settings/profile")
            token (test.i/response-anti-forgery-token response)
            {:keys [response]} (peri/request sess "/media/s3"
                                             :request-method :post
                                             :headers {"hx-request" "true"
                                                       "x-csrf-token" token}
                                             :params {:files file})]
        (is (= 404 (:status response)))))))

(deftest test-signing-uses-the-configured-presigner
  ;; Presigning is local computation, so placeholder credentials sign a URL
  ;; without any network.
  (let [presigner (ig/init-key ::aws-s3.i/s3-presigner
                               {:region "auto"
                                :endpoint-override "https://example.r2.cloudflarestorage.com"
                                :credentials-provider (ig/init-key ::aws-s3.i/credentials-provider
                                                                   {:access-key-id "test-key"
                                                                    :secret-access-key "test-secret"})})]
    (try
      (let [response (with-redefs [z/url-for (constantly "/media/uploaded")]
                       (s3/handler ::z/context {:s3-presigner presigner
                                                :media-upload-bucket "media-bucket"
                                                :media-key-prefix "media/"}
                                   :form-params {"files" file}))]
        (is (= 200 (:status response)))
        (is (str/includes? (:body response) "media-bucket"))
        (is (str/includes? (:body response) "X-Amz-Signature")))
      (finally
        (.close presigner)))))
