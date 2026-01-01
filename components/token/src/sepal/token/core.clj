(ns sepal.token.core
  (:require [sepal.token.interface.protocols :as proto]
            [taoensso.nippy :as nippy])
  (:import [java.time Instant]
           [java.util Base64]))

(defrecord NippyTokenService [secret]
  proto/TokenService

  (encode [_ data]
    {:pre [(map? data)
           (integer? (:expires-at data))]}
    (let [frozen (nippy/freeze data {:password [:cached secret]})]
      (-> (Base64/getUrlEncoder)
          (.withoutPadding)
          (.encodeToString frozen))))

  (valid? [_ token]
    (when (and token (string? token) (seq token))
      (try
        (let [data (-> (Base64/getUrlDecoder)
                       (.decode ^String token)
                       (nippy/thaw {:password [:cached secret]}))
              now (.getEpochSecond (Instant/now))]
          (when (> (:expires-at data) now)
            data))
        (catch Exception _
          nil)))))

(defn create-service
  "Create a token service with the given secret.
   Secret must be a string of at least 16 characters."
  [secret]
  {:pre [(string? secret)
         (>= (count secret) 16)]}
  (->NippyTokenService secret))
