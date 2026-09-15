(ns sepal.app.remembered-gardens
  "The cookie that lets sepal.app's Login page list the gardens this browser has
  signed in to.

  Set by the login route on the parent domain the instance is given, so the
  marketing site — a different host on the same domain — can read it. It holds
  hostnames, which are public, and no identity: a stolen copy names some gardens
  and opens none of them.

  Not HttpOnly, on purpose: the reader is JavaScript on a static page."
  (:require [clojure.data.json :as json]))

(def cookie-name "sepal_gardens")

(def ^:private max-entries 10)

(def ^:private one-year-seconds (* 365 24 60 60))

(defn- parse
  "The hostnames in an existing cookie value, or [] for anything that is not a
  JSON array of strings. A damaged cookie starts over rather than failing a
  login."
  [value]
  (try
    (let [parsed (json/read-str value)]
      (if (and (vector? parsed) (every? string? parsed)) parsed []))
    (catch Exception _ [])))

(defn remember
  "The list with hostname first, no duplicates, at most max-entries."
  [existing-value hostname]
  (->> (parse (or existing-value ""))
       (remove #{hostname})
       (cons hostname)
       (take max-entries)
       vec))

(defn cookie
  "The ring cookie map for a response. :existing is the value the request
  carried, or nil."
  [{:keys [domain existing hostname]}]
  {:value (json/write-str (remember existing hostname))
   :domain domain
   :path "/"
   :max-age one-year-seconds
   :secure true
   :same-site :lax
   :http-only false})
