(ns sepal.error.interface
  (:refer-clojure :exclude [type])
  (:require [failjure.core :as f]
            [sepal.malli.interface :as malli.i]))

(defrecord Failure [type message data]
  f/HasFailed
  (failed? [_] true)
  ;; str because ex->error copies malli's :message straight out of ex-data, and
  ;; malli sets that to the keyword :malli.core/coercion. f/message returns a
  ;; string.
  (message [_] (str message)))

(defn error
  ([type msg]
   (error type msg nil))
  ([type msg data]
   (->Failure type msg data)))

(defn ex->error [ex]
  (let [{:keys [type message data] :as exd} (ex-data ex)]
    (if (seq exd)
      (error type message data)
      (error (clojure.core/type ex) (ex-message ex) ex))))

(defn type [err]
  (:type err))

(defn message [err]
  (:message err))

(defn data [err]
  (:data err))

(defn explain [err]
  (-> err data :explain))

(defn humanize
  "The field errors of a validation failure, translated into the current
  locale."
  [err]
  (some-> err explain malli.i/humanize))

(defn error?
  ([err] (instance? Failure err))
  ([err t] (and (error? err)
                (= t (:type err)))))
