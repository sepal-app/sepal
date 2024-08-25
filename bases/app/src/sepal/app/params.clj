(ns sepal.app.params
  (:require [malli.core :as m]
            [malli.transform :as mt]))

(def params-transformer (mt/transformer
                          (mt/key-transformer {:decode keyword})
                          mt/strip-extra-keys-transformer
                          mt/default-value-transformer
                          mt/string-transformer))

(defn decode [schema params]
  (m/decode schema params params-transformer))
