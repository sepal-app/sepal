(ns sepal.postmark.interface.protocols)

(defprotocol PostmarkService
  (email [this data]))
