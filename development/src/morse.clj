(ns morse
  (:require [dev.nu.morse :as morse]))

(comment
  (morse/launch-in-proc)

  (add-tap #(morse/inspect %))
  ())
