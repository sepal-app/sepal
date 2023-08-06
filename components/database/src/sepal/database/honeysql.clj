(ns sepal.database.honeysql
  (:require [honey.sql]))

(defn- pg-json-get
  "Formats PG JSON get operation form.

   The PG JSON get form [:->> :a :b] is formatted as: a ->> 'b'
   "
  [f elements]
  [(->> elements
        (map-indexed (fn [i el]
                       (if (pos? i)
                         ;; all elements except the first are json fields so
                         ;; they should be enclosed in single quotes.
                         (str "'" (name el) "'")
                         ;; first element is the field name, shouldn't be
                         ;; enclosed in single quotes.
                         (honey.sql/format-entity el))))
        (interpose (str " " (name f) " "))
        (apply str))])

(defn init []
  (honey.sql/register-op! (keyword "@@"))
  (honey.sql/register-fn! :-> pg-json-get)
  (honey.sql/register-fn! :->> pg-json-get))
