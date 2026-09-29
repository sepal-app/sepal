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

  (decode [_ token]
    (when (and token (string? token) (seq token))
      (try
        (-> (Base64/getUrlDecoder)
            (.decode ^String token)
            (nippy/thaw {:password [:cached secret]}))
        (catch Exception _
          nil))))

  (valid? [this token]
    (when-let [data (proto/decode this token)]
      (when (> (:expires-at data) (.getEpochSecond (Instant/now)))
        data))))

(defn create-service
  "Create a token service with the given secret.
   Secret must be a string of at least 16 characters."
  [secret]
  {:pre [(string? secret)
         (>= (count secret) 16)]}
  (->NippyTokenService secret))
