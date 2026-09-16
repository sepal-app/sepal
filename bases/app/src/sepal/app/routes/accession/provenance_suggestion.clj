(ns sepal.app.routes.accession.provenance-suggestion
  "The provenance a taxon implies, for the create form to fill in.

  A cultivar, a Group and a grex are the three ranks the cultivated plant code
  governs, so a plant of any of them is by definition cultivated. Every other
  rank implies nothing and this answers blank, which leaves the field alone.

  Plain text, and the client decides whether to use it — the same shape as
  `routes/taxon/rank_guess.clj`."
  (:require [sepal.taxon.interface :as taxon.i]
            [zodiac.core :as z]))

(def ^:private cultivated-ranks #{:cultivar :group :grex})

(defn handler [{:keys [::z/context query-params]}]
  (let [{:keys [db]} context
        rank (some->> (get query-params "taxon-id")
                      (parse-long)
                      (taxon.i/get-by-id db)
                      (:taxon/rank)
                      (keyword))]
    {:status 200
     :headers {"content-type" "text/plain; charset=utf-8"}
     :body (if (contains? cultivated-ranks rank) "cultivated" "")}))
