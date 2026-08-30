(ns sepal.app.ui.table
  (:require [lambdaisland.uri :as uri]
            [sepal.app.ui.icons.heroicons :as icon]))

(defn- column-classes
  "A column's type drives its width and face; its priority drives when it is
   hidden as the viewport narrows. Priority 1 never sheds and carries no shed
   class, so `spl-shed-1` never appears in the markup."
  [{:keys [type priority]}]
  (cond-> [(str "spl-col--" (name (or type :text)))]
    (and priority (> priority 1)) (conj (str "spl-shed-" priority))))

(defn table
  "A table component.

  columns: A list of maps with keys :name, :cell, :type and :priority.

    :name     — the header text.
    :cell     — (fn [row] …) returning the cell's content.
    :type     — :identifier, :name, :date or :text. Defaults to :text.
                Drives column width and typeface: identifiers and dates are
                mono with tabular figures, because they are scanned down a
                column and compared rather than read.
    :priority — 1 never sheds. Higher numbers are hidden first as the viewport
                narrows, and are what a future column picker reads.
    :attrs    — optional (fn [row] …) returning extra attributes for this
                column's <td>. Used to carry `data-stacked`, the summary shown
                when the table collapses to one column on a phone.

  rows: A list of data. Each row is passed to (:cell column)
  row-attrs: Optional function (row) -> attrs map for each <tr> element.
             Use this to add click handlers, HTMX attributes, etc.

  Stays a real <table>. Layout comes from `table-layout: fixed` in the
  stylesheet, never from display:grid or display:block on a row — those drop
  the implicit ARIA roles a screen reader relies on to announce a cell's
  column."
  [& {:keys [columns rows row-attrs]}]
  [:table {:class "spl-table"}
   [:thead
    [:tr
     (for [col columns]
       [:th {:scope "col"
             :class (column-classes col)}
        (:name col)])]]
   [:tbody
    (for [row rows]
      [:tr (when row-attrs (row-attrs row))
       (for [col columns]
         [:td (merge {:class (column-classes col)}
                     (when-let [f (:attrs col)] (f row)))
          ((:cell col) row)])])]])

(defn card-table
  ([table]
   (card-table table nil))
  ([table paginator]
   ;; One surface level: a bordered card on the tinted page. The table inside
   ;; draws no box of its own — principle 5 caps nesting, and rules separate
   ;; peers rather than regions.
   [:div {:class "spl-table-card"}
    table
    paginator]))

(defn- page-button [& {:keys [active? label href]}]
  [:a (cond-> {:href href
               :class (cond-> ["spl-page"]
                        active? (conj "spl-page--current"))}
        active? (assoc :aria-current "page"))
   label])

(defn paginator [& {:keys [current-page page-size total href]
                    :or {total 0
                         href "#"}
                    :as _args}]
  (let [page-start (if (zero? total)
                     0
                     (-> current-page
                         (- 1)
                         (* page-size)
                         (+ 1)))
        page-href (fn [page] (-> href (uri/parse)
                                 (uri/assoc-query :page page)
                                 (uri/uri-str)))
        num-pages (int (Math/ceil (/ total page-size)))
        page-end (if (or (= current-page num-pages)
                         (zero? total))
                   total
                   (+ page-start page-size))
        previous-page-href (if (= current-page 1)
                             "#"
                             (page-href (- current-page 1)))
        next-page-href (if (= current-page num-pages)
                         "#"
                         (page-href (+ current-page 1)))
        pages (cond
                (< num-pages 6) (range 1 (inc num-pages))
                (< current-page 4) (range 1 6)
                :else (range (- current-page 2) (+ current-page 3)))]

    [:div {:class "spl-paginator"}
     [:p {:class "spl-count"}
      (format "%s\u2013%s of %s" page-start page-end total)]
     [:nav {:class "spl-pages" :aria-label "Pagination"}
      [:a {:href (page-href 1) :class "spl-page"}
       [:span {:class "sr-only"} "First page"]
       (icon/backwards-left)]
      [:a {:href previous-page-href :class "spl-page"}
       [:span {:class "sr-only"} "Previous page"]
       (icon/chevron-left)]
      (for [page pages]
        (page-button :label page
                     :active? (= current-page page)
                     :href (page-href page)))
      [:a {:href next-page-href :class "spl-page"}
       [:span {:class "sr-only"} "Next page"]
       (icon/chevron-right)]
      [:a {:href (page-href num-pages) :class "spl-page"}
       [:span {:class "sr-only"} "Last page"]
       (icon/backwards-right)]]]))
