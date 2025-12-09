(ns sepal.mail.interface.protocols)

(defprotocol MailClient
  (send-message [this message]
    "Send an email message. Message is a map with keys:
     :from, :to, :subject, :body, and optional :content-type (default text/plain)"))
