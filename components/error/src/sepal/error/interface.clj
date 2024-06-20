(ns sepal.error.interface
  (:refer-clojure :exclude [type]))

(defn error
  ([type msg]
   (error type msg nil))
  ([type msg data]
   ^::error {::type type
             ::message msg
             ::data data}))

(defn ex->error [ex]
  (let [{:keys [type message data] :as exd} (ex-data ex)]
    (if (seq exd)
      (error type message data)
      (error (clojure.core/type ex) (ex-message ex) ex))))

(defn type [err]
  (::type err))

(defn message [err]
  (::message err))

(defn data [err]
  (::data err))

(defn explain [err]
  (-> err data :explain :errors))

(defn error?
  ([err] (true? (-> err meta ::error)))
  ([err type] (and (error? err)
                   (= type (type err)))))

(defn throw-error [err]
  (throw (ex-info (message err) err)))

(defn throw-if-error
  "If err is an error then throw it else return it."
  [err]
  (if (error? err)
    (throw-error err)
    err))
