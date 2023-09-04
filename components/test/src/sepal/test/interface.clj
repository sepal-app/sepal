(ns sepal.test.interface
  (:require [sepal.test.core :as core]))

(defn create-system-fixture
  [config invoke keys]
  (core/create-system-fixture config invoke keys))
