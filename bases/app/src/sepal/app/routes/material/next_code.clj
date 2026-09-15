(ns sepal.app.routes.material.next-code
  "The material code suggestion, fetched when the accession select changes.

  Material is numbered within its accession, so the suggestion cannot be
  rendered with the form: two of the three ways in do not know an accession
  yet. This endpoint answers with the Code control, which swaps itself."
  (:require [sepal.app.codes :as codes]
            [sepal.app.html :as html]
            [sepal.app.routes.material.form :as material.form]
            [sepal.material.interface :as material.i]
            [zodiac.core :as z]))

(defn handler [{:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        accession-id (some-> (get query-params "accession-id") parse-long)
        {:keys [template]} (codes/material db)]
    (html/render-partial
      (material.form/code-input
        :accession-id accession-id
        :value (when accession-id
                 (material.i/next-code db template accession-id))))))
