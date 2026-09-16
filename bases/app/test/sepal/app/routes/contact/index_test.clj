(ns sepal.app.routes.contact.index-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.app.routes.contact.index :as contact.index]))

(deftest test-completion-text-leads-with-the-name
  (is (= "Jane Smith (Royal Botanic Gardens)"
         (contact.index/completion-text {:contact/name "Jane Smith"
                                         :contact/business "Royal Botanic Gardens"}))))

(deftest test-completion-text-falls-back-past-a-missing-business
  (testing "email, then phone — a personal contact still has to be
            distinguishable from another of the same name"
    (is (= "Jane Smith (jane@example.com)"
           (contact.index/completion-text {:contact/name "Jane Smith"
                                           :contact/business ""
                                           :contact/email "jane@example.com"})))
    (is (= "Jane Smith (+44 20 7946 0000)"
           (contact.index/completion-text {:contact/name "Jane Smith"
                                           :contact/business nil
                                           :contact/email nil
                                           :contact/phone "+44 20 7946 0000"})))))

(deftest test-completion-text-is-the-name-alone-when-there-is-nothing-else
  (testing "the old format was `business (name)`, so a contact with no
            business rendered as \" (Kew Seed Bank)\" — a leading space and
            empty parens"
    (is (= "Kew Seed Bank"
           (contact.index/completion-text {:contact/name "Kew Seed Bank"
                                           :contact/business nil
                                           :contact/email nil
                                           :contact/phone nil})))
    (is (= "Kew Seed Bank"
           (contact.index/completion-text {:contact/name "Kew Seed Bank"
                                           :contact/business "  "
                                           :contact/email ""})))))

(deftest test-completion-text-does-not-repeat-itself
  (testing "a business contact whose business is its name"
    (is (= "Kew Seed Bank"
           (contact.index/completion-text {:contact/name "Kew Seed Bank"
                                           :contact/business "Kew Seed Bank"})))
    (testing "and still reaches past it to the next thing that differs"
      (is (= "Kew Seed Bank (seed@kew.org)"
             (contact.index/completion-text {:contact/name "Kew Seed Bank"
                                             :contact/business "Kew Seed Bank"
                                             :contact/email "seed@kew.org"}))))))
