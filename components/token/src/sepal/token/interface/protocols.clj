(ns sepal.token.interface.protocols)

(defprotocol TokenService
  (encode [this data]
    "Encode a map as an encrypted, URL-safe base64 token.
     Data must include :expires-at (epoch seconds integer).
     Returns a string token.")

  (decode [this token]
    "Decode a token without checking its expiration.
     Returns the decoded data map if the token was encoded with this service's
     secret and has not been tampered with, nil otherwise.")

  (valid? [this token]
    "Decode and validate a token.
     Returns the decoded data map if token is valid and not expired.
     Returns nil if token is invalid, tampered, or expired.

     NOTE: This only validates the token itself (signature + expiration).
     Callers are responsible for additional business logic validation
     (e.g., checking if user exists, user status, etc.)"))
