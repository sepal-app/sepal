(ns sepal.token.interface
  (:require [integrant.core :as ig]
            [sepal.token.core :as core]
            [sepal.token.interface.protocols :as proto])
  (:import [java.time Instant]))

;; =============================================================================
;; Protocol Functions
;; =============================================================================

(defn encode
  "Encode data as an encrypted, URL-safe base64 token.
   Data must be a map and include :expires-at (epoch seconds integer)."
  [service data]
  {:pre [(map? data)
         (integer? (:expires-at data))]}
  (proto/encode service data))

(defn valid?
  "Decode and validate a token.
   Returns the decoded data map if valid and not expired, nil otherwise.

   NOTE: This only validates the token itself (signature + expiration).
   Callers are responsible for additional business logic validation
   (e.g., checking if user exists, user status, etc.)"
  [service token]
  (proto/valid? service token))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn expires-in
  "Calculate expires-at timestamp for n seconds from now."
  [seconds]
  (+ (.getEpochSecond (Instant/now)) seconds))

(defn expires-in-minutes
  "Calculate expires-at timestamp for n minutes from now."
  [minutes]
  (expires-in (* minutes 60)))

(defn expires-in-hours
  "Calculate expires-at timestamp for n hours from now."
  [hours]
  (expires-in (* hours 60 60)))

(defn expires-in-days
  "Calculate expires-at timestamp for n days from now."
  [days]
  (expires-in (* days 24 60 60)))

;; =============================================================================
;; Integrant Component
;; =============================================================================

(defmethod ig/init-key ::service [_ {:keys [secret]}]
  (core/create-service secret))
