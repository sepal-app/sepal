(ns sepal.app.routes.settings.profile-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [peridot.core :as peri]
            [sepal.app.test :as app.test]
            [sepal.app.test.system :refer [*db* default-system-fixture]]
            [sepal.i18n.interface :as i18n]
            [sepal.test.interface :as test.i]
            [sepal.user.interface :as user.i])
  (:import [org.jsoup Jsoup]))

(use-fixtures :once default-system-fixture)

(defn- flash-banner-text [body]
  (some-> (.selectFirst body ".banner-text") (.text)))

(defn- create-user! [db role password]
  (let [email (str (name role) "-" (random-uuid) "@test.com")]
    (user.i/create! db {:email email :password password :role role})
    email))

(deftest test-profile-update
  (testing "POST /settings/profile with valid data updates profile and shows success message"
    (let [password "testpassword123"
          email (create-user! *db* :admin password)
          sess (app.test/login email password)
          {:keys [response] :as sess} (peri/request sess "/settings/profile")
          token (test.i/response-anti-forgery-token response)
          {:keys [response] :as sess} (peri/request sess "/settings/profile"
                                                    :request-method :post
                                                    :params {:__anti-forgery-token token
                                                             :full-name "Updated Name"
                                                             :email email})
          _ (is (= 303 (:status response)) "Should redirect after successful update")
          {:keys [response]} (peri/follow-redirect sess)]
      (is (= 200 (:status response)))
      (let [body (Jsoup/parse ^String (:body response))]
        (is (= "Profile updated successfully" (flash-banner-text body)))))))

(def ^:private es
  (i18n/parse-catalog "es" "msgid \"\"
msgstr \"\"
\"Plural-Forms: nplurals=2; plural=(n != 1);\\n\"

msgid \"Profile\"
msgstr \"Perfil\"

msgid \"Language\"
msgstr \"Idioma\"

msgid \"Taxa\"
msgstr \"Taxones\"

msgid \"Profile updated successfully\"
msgstr \"Perfil actualizado\"
"))

(defn- with-spanish [f]
  (i18n/load-catalogs! {"es" es})
  (try (f) (finally (i18n/load-catalogs! {}))))

(defn- language-label [response]
  (some-> (Jsoup/parse ^String (:body response)) (.selectFirst "label[for=language]") (.text)))

(defn- set-language! [sess language email]
  (let [{:keys [response] :as sess} (peri/request sess "/settings/profile")
        token (test.i/response-anti-forgery-token response)]
    (peri/request sess "/settings/profile"
                  :request-method :post
                  :params {:__anti-forgery-token token
                           :full-name ""
                           :email email
                           :language language})))

(deftest test-language
  (with-spanish
    (fn []
      (let [password "testpassword123"
            email (create-user! *db* :reader password)
            sess (app.test/login email password)]

        (testing "the browser's language is the default"
          (let [{:keys [response]} (peri/request sess "/settings/profile"
                                                 :headers {"accept-language" "es-MX,es;q=0.9"})]
            (is (= "Idioma" (language-label response))))
          (let [{:keys [response]} (peri/request sess "/settings/profile"
                                                 :headers {"accept-language" "fr"})]
            (is (= "Language" (language-label response)) "no French catalog, so English")))

        (testing "the selector offers the browser default and each catalog"
          (let [{:keys [response]} (peri/request sess "/settings/profile")
                options (->> (.select (Jsoup/parse ^String (:body response)) "select[name=language] option")
                             (map #(vector (.attr % "value") (.text %))))]
            (is (= [["" "Browser default"] ["en" "English"] ["es" "Español"]] options))))

        (testing "English can be chosen explicitly, and beats a Spanish browser"
          (let [{:keys [response] :as sess} (set-language! sess "en" email)
                _ (is (= 303 (:status response)))
                {:keys [response]} (peri/request sess "/settings/profile"
                                                 :headers {"accept-language" "es"})]
            (is (= "en" (:user/language (user.i/get-by-email *db* email))))
            (is (= "Language" (language-label response)))
            (is (= "en" (some-> (Jsoup/parse ^String (:body response))
                                (.selectFirst "select[name=language] option[selected]")
                                (.attr "value"))))
            (is (= "en" (.attr (.selectFirst (Jsoup/parse ^String (:body response)) "html") "lang")))))

        (testing "a saved choice beats the browser"
          (let [{:keys [response] :as sess} (set-language! sess "es" email)
                _ (is (= 303 (:status response)))
                {:keys [response]} (peri/request sess "/settings/profile"
                                                 :headers {"accept-language" "en-US"})]
            (is (= "es" (:user/language (user.i/get-by-email *db* email))))
            (is (= "Idioma" (language-label response)))
            (is (= "Perfil actualizado" (flash-banner-text (Jsoup/parse ^String (:body response))))
                "the confirmation is in the language just chosen")
            (is (some #(= "Taxones" (.text %))
                      (.select (Jsoup/parse ^String (:body response)) ".spl-rail .spl-nav-label"))
                "the section rail is a lazy seq, rendered after the handler returns")
            (is (= "es" (.attr (.selectFirst (Jsoup/parse ^String (:body response)) "html") "lang")))
            (is (= "es" (some-> (Jsoup/parse ^String (:body response))
                                (.selectFirst "select[name=language] option[selected]")
                                (.attr "value"))))))

        (testing "choosing the browser default clears it"
          (let [{:keys [response] :as sess} (set-language! sess "" email)
                _ (is (= 303 (:status response)))
                {:keys [response]} (peri/request sess "/settings/profile"
                                                 :headers {"accept-language" "en-US"})]
            (is (nil? (:user/language (user.i/get-by-email *db* email))))
            (is (= "Language" (language-label response)))))

        (testing "a language with no catalog is refused"
          (let [{:keys [response]} (set-language! sess "fr" email)]
            (is (not= 303 (:status response)))
            (is (nil? (:user/language (user.i/get-by-email *db* email))))))))))
