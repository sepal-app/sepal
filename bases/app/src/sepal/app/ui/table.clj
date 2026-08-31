(ns sepal.app.ui.table
  (:require [lambdaisland.uri :as uri]
            [sepal.app.ui.icons.heroicons :as icon]))

(def rows-container-id
  "The tbody infinite scroll appends into. Shared so a route's partial response
  and the page's initial render agree on the target without each list naming it."
  "table-rows")

(defn- column-classes
  "A column's type drives its width and face; its priority drives when it is
   hidden as the viewport narrows. Priority 1 never sheds and carries no shed
   class, so `spl-shed-1` never appears in the markup."
  [{:keys [type priority]}]
  (cond-> [(str "spl-col--" (name (or type :text)))]
    (and priority (> priority 1)) (conj (str "spl-shed-" priority))))

(defn next-page-url
  "The URL for the page after this one, or nil at the end of the list.

  `rows=1` asks the handler for the <tr>s alone — the shape the sentinel swaps
  itself out for. Shared so every list agrees on the parameter rather than each
  inventing one."
  [& {:keys [href page page-size total]}]
  (when (and href page page-size total (< (* page page-size) total))
    (-> href
        (uri/parse)
        (uri/assoc-query :page (inc page) :rows 1)
        (uri/uri-str))))

(defn sentinel-row
  "An empty row that fetches the next page when it scrolls into view, and
  replaces itself with those rows plus the next sentinel.

  `hx-swap outerHTML` rather than beforeend: the sentinel is the last row, so
  swapping itself out is what keeps exactly one sentinel in the table. Appending
  would leave the spent sentinel behind and fetch the same page forever."
  [next-page-url column-count]
  [:tr {:class "spl-sentinel"
        :hx-get next-page-url
        :hx-trigger "revealed"
        :hx-swap "outerHTML"}
   [:td {:colspan column-count}]])

(defn end-of-list
  "Shown once every row has been loaded, so the list has a visible bottom
  rather than simply stopping."
  [column-count]
  [:tr {:class "spl-end"}
   [:td {:colspan column-count} "End of list"]])

(defn rows-only
  "Just the <tr>s, for the initial render and for an infinite-scroll response.

  Both paths render through this so a row appended by scrolling is built the
  same way as a row present on load."
  [& {:keys [columns rows row-attrs next-page-url]}]
  (let [n (count columns)]
    (list
      (for [row rows]
        [:tr (when row-attrs (row-attrs row))
         (for [col columns]
           [:td (merge {:class (column-classes col)}
                       (when-let [f (:attrs col)] (f row)))
            ((:cell col) row)])])
      (if next-page-url
        (sentinel-row next-page-url n)
        (end-of-list n)))))

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
  [& {:keys [columns rows row-attrs next-page-url]}]
  [:table {:class "spl-table"}
   [:thead
    [:tr
     (for [col columns]
       [:th {:scope "col"
             :class (column-classes col)}
        (:name col)])]]
   [:tbody {:id rows-container-id}
    (rows-only :columns columns
               :rows rows
               :row-attrs row-attrs
               :next-page-url next-page-url)]])

(defn card-table
  "The list surface. Rows scroll inside it; the header stays put.

  There is no pager: lists load the next page as you reach the bottom, and the
  row count lives in the toolbar beside the search. See `sentinel-row`."
  ([table]
   [:div {:class "spl-table-card"}
    [:div {:class "spl-table-scroll"} table]])
  ([table _paginator]
   (card-table table)))

(defn row-count
  "\"1–25 of 1,284\", for the toolbar. The count belongs beside the search that
  changes it rather than at the foot of a list you have to reach to read."
  [& {:keys [loaded total]}]
  [:p {:class "spl-count"}
   (if (or (nil? total) (zero? total))
     "No rows"
     (format "%,d of %,d" (or loaded 0) total))])

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
