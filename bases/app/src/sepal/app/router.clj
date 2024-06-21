(ns sepal.app.router
  (:require [reitit.core :as r]))

(defn url-for
  ([router name-or-path]
   (url-for router name-or-path nil nil))
  ([router name-or-path args]
   (url-for router name-or-path args nil))
  ([router name-or-path args query-params]
   (if (string? name-or-path)
     name-or-path
     (-> router
         (r/match-by-name name-or-path args)
         (r/match->path query-params)))))
