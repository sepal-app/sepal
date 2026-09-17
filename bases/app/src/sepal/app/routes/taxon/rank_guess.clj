(ns sepal.app.routes.taxon.rank-guess
  "The rank a name implies, for the create form to fill in while you type.

  Plain text rather than markup, and the client decides whether to use it.
  Swapping the control itself would drop whatever the page has wired to it, so
  `routes/taxon/form.ts` sets the value instead — and only while you have not
  set the rank yourself."
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
