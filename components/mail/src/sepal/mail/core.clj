(ns sepal.mail.core
  (:require [sepal.mail.interface.protocols :as mail.p])
  (:import [jakarta.mail Authenticator Message$RecipientType PasswordAuthentication Session Transport]
           [jakarta.mail.internet InternetAddress MimeMessage]
           [java.util Properties]))

(defn create-session
  "Create a Jakarta Mail Session from configuration options.
   Options:
     :host     - SMTP server hostname
     :port     - SMTP server port
     :username - SMTP auth username
     :password - SMTP auth password
     :auth     - Enable SMTP authentication (boolean)
     :tls      - TLS mode: \"starttls\", \"ssl\", or \"none\""
  [{:keys [host port username password auth tls]}]
  (let [props (doto (Properties.)
                (.put "mail.smtp.host" (str host))
                (.put "mail.smtp.port" (str port))
                (.put "mail.smtp.auth" (str (boolean auth))))
        _ (case tls
            "starttls" (.put props "mail.smtp.starttls.enable" "true")
            "ssl" (.put props "mail.smtp.ssl.enable" "true")
            "none" nil
            nil)
        authenticator (when auth
                        (proxy [Authenticator] []
                          (getPasswordAuthentication []
                            (PasswordAuthentication. username password))))]
    (Session/getInstance props authenticator)))

(defn- to-internet-addresses
  "Convert a string or collection of strings to InternetAddress array."
  [addr]
  (let [addrs (if (string? addr) [addr] addr)]
    (into-array InternetAddress (map #(InternetAddress. %) addrs))))

(defn- create-mime-message
  "Create a MimeMessage from a session and message map."
  [^Session session {:keys [from to subject body content-type]}]
  (let [content-type (or content-type "text/plain")]
    (doto (MimeMessage. session)
      (.setFrom (InternetAddress. from))
      (.setRecipients Message$RecipientType/TO (to-internet-addresses to))
      (.setSubject subject)
      (.setContent body content-type))))

(deftype SmtpClient [^Session session]
  mail.p/MailClient

  (send-message [_ message]
    (let [mime-message (create-mime-message session message)]
      (Transport/send mime-message)
      {:status :sent})))
