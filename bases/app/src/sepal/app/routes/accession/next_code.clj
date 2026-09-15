(ns sepal.app.routes.accession.next-code
  "The accession code suggestion, fetched on demand.

  The create form prefills a code when it renders, but a code read minutes ago
  can be taken by the time you save. The refresh control beside the field asks
  for the current one."
  (:require [sepal.accession.interface :as accession.i]
            [sepal.app.codes :as codes]
            [sepal.app.datetime :as datetime]
            [sepal.app.html :as html]
            [sepal.app.routes.accession.form :as accession.form]
            [zodiac.core :as z]))

(defn handler [{:keys [::z/context]}]
  (let [{:keys [db timezone]} context
        {:keys [template]} (codes/accession db)]
    (html/render-partial
      (accession.form/code-input
        :value (accession.i/next-code db template (datetime/today timezone))
        :help accession.form/code-help))))
