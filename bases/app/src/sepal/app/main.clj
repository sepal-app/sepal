(ns sepal.app.main
  (:require [sepal.app.system :as system])
  (:gen-class))

(def default-environment "local")

(defn- get-env-variable [name default-val]
  (or (System/getenv name) default-val))

(defn -main [& _]
  (try
    (let [profile (-> "SEPAL_ENVIRONMENT"
                      (get-env-variable default-environment)
                      keyword)]
      (system/start! profile))
    (catch Exception exc
      (println (ex-message exc))
      (println exc))))
