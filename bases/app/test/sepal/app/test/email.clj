(ns sepal.app.test.email
  "Email addresses for tests that drive a real browser.

  The e2e logins used to draw from `user.spec/email` with malli's generator.
  That is the wrong tool for \"give me a unique address\": its range is whatever
  the regex happens to allow, which is not the same as what a browser will
  submit, and the two drifted.")

(defn unique
  "An address no other test has used, in a shape a browser will submit."
  []
  (str "e2e-" (random-uuid) "@example.com"))
