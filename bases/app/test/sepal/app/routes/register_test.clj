(ns sepal.app.routes.register-test
  (:require [clojure.test :refer :all]
            [sepal.app.test.system :refer [*app* ;; *db* *system*
                                           default-system-fixture]]
            [sepal.app.routes.register.index :as register]))

(use-fixtures :once default-system-fixture)

;; (deftest register-test
;;   (testing "something"
;;     (let [resp (*app* {:request-method :get :uri "/register"})
;;           body (Jsoup/parse (:body resp))]
;;       (tap> (str "resp: " resp))
;;       (is (= 200 (:status resp)))
;;       (tap> (.select body "input"))
;;       ;; TODO:
;;       )))
