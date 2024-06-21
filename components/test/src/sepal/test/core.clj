(ns sepal.test.core
  (:require [integrant.core :as ig]))

(defn create-system-fixture
  [config invoke keys]
  (fn [f]
    (ig/load-namespaces config)
    (let [system (ig/init config keys)]
      (try
        (invoke system f)
        (finally
          (ig/halt! system))))))
