(ns sepal.app.routes.taxon.rank-guess
  "The rank a name implies, for the create form to fill in while you type.

  Plain text rather than markup, and the client decides whether to use it. The
  rank control is a SlimSelect, so swapping it would leave the widget it
  generated orphaned beside the replacement; `routes/taxon/form.ts` sets the
  value through SlimSelect instead, and only while you have not set the rank
  yourself."
  (:require [sepal.taxon.interface.name :as taxon.name]))

(defn handler [{:keys [query-params]}]
  {:status 200
   :headers {"content-type" "text/plain; charset=utf-8"}
   ;; Blank when the name implies no rank, so the field is left alone rather
   ;; than cleared.
   :body (or (some-> (get query-params "name")
                     (taxon.name/guess-rank)
                     (name))
             "")})
