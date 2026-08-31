(ns sepal.app.ui.taxon-name
  "The one place a scientific name becomes markup.

   Principle 2 of the design: botanical typography is correctness, not
   decoration. Centralising it here is what stops the convention drifting
   between the list, the panel, the breadcrumb, a form field and a sentence in
   the activity feed.

   The rule itself lives in `sepal.taxon.interface.name`, which returns data.
   This namespace only decides which element and class each segment gets."
  (:require [clojure.string :as str]
            [sepal.taxon.interface.name :as taxon.name]))

(defn render
  "Hiccup for a scientific name.

   Italicised segments become `<i class=spl-sci>`, which the stylesheet sets in
   Source Serif 4. Everything else — the hybrid marker, the connecting terms, a
   cultivar epithet — stays in the sans face. `author` is a separate column on
   taxon and is always upright and dimmed.

   Works in a table cell and in running prose; the activity feed needs the
   second."
  [s & {:keys [author]}]
  (into [:span {:class "spl-name"}]
        (cond-> (mapv (fn [{:keys [text role]}]
                        (if (= role :scientific)
                          [:i {:class "spl-sci"} text]
                          [:span text]))
                      (taxon.name/segments s))
          (not (str/blank? author))
          (conj [:span {:class "spl-authority"} (str " " author)]))))
