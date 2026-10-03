(ns sepal.app.routes.media.s3-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is use-fixtures]]
            [integrant.core :as ig]
            [peridot.core :as peri]
            [sepal.app.json :as json]
            [sepal.app.routes.media.s3 :as s3]
            [sepal.app.test :as app.test]
            [sepal.app.test.fixtures :as tf]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.aws-s3.interface :as aws-s3.i]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(use-fixtures :once default-system-fixture)

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
                                             :params {:filename "rose.jpg"
                                                      :contentType "image/jpeg"})]
        (is (= 404 (:status response)))))))

(defn- test-presigner []
  ;; Presigning is local computation, so placeholder credentials sign a URL
  ;; without any network.
  (ig/init-key ::aws-s3.i/s3-presigner
               {:region "auto"
                :endpoint-override "https://example.r2.cloudflarestorage.com"
                :credentials-provider (ig/init-key ::aws-s3.i/credentials-provider
                                                   {:access-key-id "test-key"
                                                    :secret-access-key "test-secret"})}))

(defn- sign [presigner form-params]
  (s3/handler ::z/context {:s3-presigner presigner
                           :media-upload-bucket "media-bucket"
                           :media-key-prefix "media/"}
              :form-params form-params))

(deftest test-signing-answers-what-uppy-signs-with
  (let [presigner (test-presigner)]
    (try
      (let [response (sign presigner {"filename" "rose.JPG" "contentType" "Image/JPEG"})
            {:strs [url key headers]} (json/parse-str (:body response))]
        (is (= 200 (:status response)))
        (is (str/includes? url "media-bucket"))
        (is (str/includes? url "X-Amz-Signature"))
        (is (re-matches #"media/[0-9a-f]+\.JPG" key) "the server chooses the key, under the garden's prefix")
        (is (str/includes? url key) "the URL is signed for that key")
        (is (= {"content-type" "image/jpeg"} headers)
            "the browser sends the lowercased type the URL was signed with"))
      (finally
        (.close presigner)))))

(deftest test-a-file-without-an-extension-gets-a-key-without-one
  (let [presigner (test-presigner)]
    (try
      (let [response (sign presigner {"filename" "README" "contentType" "text/plain"})]
        (is (re-matches #"media/[0-9a-f]+" (get (json/parse-str (:body response)) "key"))))
      (finally
        (.close presigner)))))

(deftest test-signing-refuses-a-request-without-a-content-type
  (let [presigner (test-presigner)]
    (try
      (is (= 422 (:status (sign presigner {"filename" "rose.jpg"}))))
      (finally
        (.close presigner)))))
