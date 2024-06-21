(ns sepal.app.routes.register-test
  (:require [clojure.test :refer :all]
            #_[sepal.app.test.system :refer [*app* ;; *db* *system*
                                             default-system-fixture]]
            #_[sepal.app.routes.register.index :as register]))

#_(use-fixtures :once default-system-fixture)

;; (deftest register-test
;;   (testing "something"
;;     (let [resp (*app* {:request-method :get :uri "/register"})
;;           body (Jsoup/parse (:body resp))]
;;       (tap> (str "resp: " resp))
;;       (is (= 200 (:status resp)))
;;       (tap> (.select body "input"))
;;       ;; TODO:
;;       )))
