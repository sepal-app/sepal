(ns sepal.token.interface-test
  (:require [clojure.test :refer [deftest is testing]]
            [sepal.token.core :as core]
            [sepal.token.interface :as token.i])
  (:import [java.time Instant]))

(def ^:private test-secret "test-secret-1234")

(defn- now-epoch []
  (.getEpochSecond (Instant/now)))

;; =============================================================================
;; Roundtrip Tests
;; =============================================================================

(deftest test-encode-decode-roundtrip
  (let [service (core/create-service test-secret)
        data {:email "test@example.com"
              :role :editor
              :expires-at (token.i/expires-in-hours 1)}
        token (token.i/encode service data)]
    (is (string? token))
    (is (= data (token.i/valid? service token)))))

(deftest test-roundtrip-preserves-data-types
  (testing "Keywords, strings, integers, booleans, nested maps, vectors"
    (let [service (core/create-service test-secret)
          data {:string "hello"
                :keyword :value
                :integer 42
                :boolean true
                :nil-value nil
                :vector [1 2 3]
                :nested {:a {:b :c}}
                :expires-at (token.i/expires-in-hours 1)}
          token (token.i/encode service data)]
      (is (= data (token.i/valid? service token))))))

(deftest test-encoding-roundtrip-consistency
  (testing "Multiple encodes of same data all decode to same value"
    (let [service (core/create-service test-secret)
          data {:email "test@example.com"
                :expires-at (+ (now-epoch) 3600)}
          token1 (token.i/encode service data)
          token2 (token.i/encode service data)]
      ;; Tokens may differ (nippy includes random salt) but both decode correctly
      (is (= data (token.i/valid? service token1)))
      (is (= data (token.i/valid? service token2))))))

;; =============================================================================
;; Expiration Tests
;; =============================================================================

(deftest test-expired-token-returns-nil
  (let [service (core/create-service test-secret)
        data {:email "test@example.com"
              :expires-at (- (now-epoch) 1)}
        token (token.i/encode service data)]
    (is (nil? (token.i/valid? service token)))))

(deftest test-token-expires-at-boundary
  (testing "Token expired exactly at expires-at time returns nil"
    (let [service (core/create-service test-secret)
          now (now-epoch)
          data {:email "test@example.com"
                :expires-at now}
          token (token.i/encode service data)]
      (is (nil? (token.i/valid? service token))))))

(deftest test-token-valid-before-expiry
  (let [service (core/create-service test-secret)
        data {:email "test@example.com"
              :expires-at (+ (now-epoch) 60)}
        token (token.i/encode service data)]
    (is (some? (token.i/valid? service token)))))

;; =============================================================================
;; Tampering Tests
;; =============================================================================

(deftest test-tampered-token-returns-nil
  (let [service (core/create-service test-secret)
        data {:email "test@example.com"
              :expires-at (token.i/expires-in-hours 1)}
        token (token.i/encode service data)]
    (testing "suffix added"
      (is (nil? (token.i/valid? service (str token "x")))))
    (testing "prefix added"
      (is (nil? (token.i/valid? service (str "x" token)))))
    (testing "character substituted"
      (let [mid (/ (count token) 2)
            tampered (str (subs token 0 mid) "X" (subs token (inc mid)))]
        (is (nil? (token.i/valid? service tampered)))))
    (testing "truncated"
      (is (nil? (token.i/valid? service (subs token 0 (- (count token) 5))))))))

;; =============================================================================
;; Secret Validation Tests
;; =============================================================================

(deftest test-wrong-secret-returns-nil
  (let [service1 (core/create-service "test-secret-1234")
        service2 (core/create-service "different-secret!")
        data {:email "test@example.com"
              :expires-at (token.i/expires-in-hours 1)}
        token (token.i/encode service1 data)]
    (is (nil? (token.i/valid? service2 token)))))

(deftest test-different-secrets-produce-different-tokens
  (let [service1 (core/create-service "secret-one-16chars")
        service2 (core/create-service "secret-two-16chars")
        data {:email "test@example.com"
              :expires-at (token.i/expires-in-hours 1)}
        token1 (token.i/encode service1 data)
        token2 (token.i/encode service2 data)]
    (is (not= token1 token2))))

;; =============================================================================
;; Malformed Input Tests
;; =============================================================================

(deftest test-invalid-tokens-return-nil
  (let [service (core/create-service test-secret)]
    (testing "empty string"
      (is (nil? (token.i/valid? service ""))))
    (testing "nil"
      (is (nil? (token.i/valid? service nil))))
    (testing "invalid base64"
      (is (nil? (token.i/valid? service "not-valid-base64!!!"))))
    (testing "valid base64 but invalid data"
      (is (nil? (token.i/valid? service "YWJjZGVm"))))
    (testing "whitespace"
      (is (nil? (token.i/valid? service "   "))))))

;; =============================================================================
;; Precondition Tests
;; =============================================================================

(deftest test-encode-preconditions
  (let [service (core/create-service test-secret)]
    (testing "requires expires-at"
      (is (thrown? AssertionError
                   (token.i/encode service {:email "test@example.com"}))))
    (testing "expires-at must be integer"
      (is (thrown? AssertionError
                   (token.i/encode service {:expires-at "not-an-integer"}))))
    (testing "requires map"
      (is (thrown? AssertionError
                   (token.i/encode service "not a map"))))))

(deftest test-secret-preconditions
  (testing "minimum length"
    (is (thrown? AssertionError (core/create-service "short")))
    (is (thrown? AssertionError (core/create-service "exactly15chars!")))
    (is (some? (core/create-service "exactly16chars!!"))))
  (testing "must be string"
    (is (thrown? AssertionError (core/create-service nil)))))

;; =============================================================================
;; URL Safety Tests
;; =============================================================================

(deftest test-token-is-url-safe
  (testing "Token contains only URL-safe base64 characters"
    (let [service (core/create-service test-secret)
          tokens (repeatedly 50
                             #(token.i/encode service
                                              {:random (rand-int 1000000)
                                               :expires-at (token.i/expires-in-hours 1)}))]
      (doseq [token tokens]
        (is (re-matches #"[A-Za-z0-9_-]+" token)
            "Token should only contain URL-safe characters")))))

;; =============================================================================
;; Helper Function Tests
;; =============================================================================

(deftest test-expires-in-helpers
  (let [now (now-epoch)]
    (testing "expires-in"
      (let [result (token.i/expires-in 60)]
        (is (>= result (+ now 59)))
        (is (<= result (+ now 61)))))
    (testing "expires-in-minutes"
      (let [result (token.i/expires-in-minutes 10)]
        (is (>= result (+ now 599)))
        (is (<= result (+ now 601)))))
    (testing "expires-in-hours"
      (let [result (token.i/expires-in-hours 2)]
        (is (>= result (+ now 7199)))
        (is (<= result (+ now 7201)))))
    (testing "expires-in-days"
      (let [result (token.i/expires-in-days 1)]
        (is (>= result (+ now 86399)))
        (is (<= result (+ now 86401)))))))

;; =============================================================================
;; Security Tests
;; =============================================================================

(deftest test-different-data-produces-different-tokens
  (let [service (core/create-service test-secret)
        token1 (token.i/encode service {:id 1 :expires-at (token.i/expires-in-hours 1)})
        token2 (token.i/encode service {:id 2 :expires-at (token.i/expires-in-hours 1)})]
    (is (not= token1 token2))))

(deftest test-no-plaintext-data-in-token
  (testing "Plaintext data should not be visible in token"
    (let [service (core/create-service test-secret)
          email "visible-email@example.com"
          token (token.i/encode service {:email email
                                         :expires-at (token.i/expires-in-hours 1)})]
      (is (not (.contains token "visible")))
      (is (not (.contains token "email")))
      (is (not (.contains token "@"))))))
