(ns sepal.app.ui.table
  (:require [clojure.string :as str]
            [lambdaisland.uri :as uri]
            [sepal.app.ui.icons.heroicons :as icon]))

(defn summary
  "Joins a row's secondary fields into the one line the first cell shows below
  640px. Defined here so the four lists cannot drift apart on the separator,
  and so a keyword or a number is as welcome as a string."
  [& parts]
  (->> parts
       (keep #(when % (str %)))
       (remove str/blank?)
       (str/join " · ")))

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

(def sentinel-id
  "The row the next page swaps into. Exactly one exists at a time — the fetch
  replaces it with the new rows plus a fresh sentinel — so a fixed id is safe."
  "table-sentinel")

(def ^:private prefetch-offset
  "How many rows above the last one the next page starts loading. Three is
  enough that the request is usually in flight before the reader reaches the
  bottom, and few enough that a fast scroll doesn't fetch pages nobody reads."
  3)

(defn prefetch-row
  "A zero-height row that fetches the next page when it scrolls into view.

  It sits `prefetch-offset` rows above the sentinel, so the rows are usually
  already loading by the time the reader reaches the bottom. htmx's `intersect`
  takes only `root:` and `threshold:` — there is no rootMargin to fire it early
  — so position in the table is how that margin is expressed.

  It is its own row rather than attributes merged into a data row: list rows
  already carry an `hx-get` that loads the resource panel on click, and a
  second one would replace it.

  `hx-indicator` points at the sentinel so the spinner appears where the rows
  will land rather than three rows above it."
  [next-page-url column-count]
  [:tr {:class "spl-prefetch"
        ;; Nothing to announce: it is a scroll position, not a row of the data.
        :aria-hidden "true"
        :hx-get next-page-url
        ;; `intersect`, not `revealed`. htmx implements `revealed` by listening
        ;; for scroll on window and comparing against window.innerHeight, so it
        ;; never fires inside a scrolling container — and this shell is
        ;; viewport-locked, so the window never scrolls at all. `intersect` uses
        ;; IntersectionObserver, which computes visibility through the clip
        ;; chain and works in either layout.
        :hx-trigger "intersect once"
        :hx-target (str "#" sentinel-id)
        :hx-swap "outerHTML"
        :hx-indicator (str "#" sentinel-id)
        ;; The swap replaces the sentinel, not this row, so it has to clear
        ;; itself away — otherwise a spent trigger is left behind per page.
        (keyword "hx-on::after-request") "this.remove()"}
   [:td {:colspan column-count}]])

(defn sentinel-row
  "The swap target for the next page, and where its spinner shows.

  It carries no trigger of its own: the row `prefetch-offset` above it fires the
  fetch. Two triggers would race — a fast scroll fires both, and the second
  response inserts the same page a second time."
  [column-count]
  [:tr {:class "spl-sentinel"
        :id sentinel-id}
   [:td {:colspan column-count}
    [:span {:class "spl-sentinel-spinner" :aria-hidden "true"}]
    ;; A live region that is already in the DOM when its content appears. The
    ;; text is display:none until htmx adds `htmx-request`, and that change is
    ;; what gets announced — a region inserted with its text already present
    ;; announces nothing.
    [:span {:class "spl-sentinel-status" :role "status"}
     [:span {:class "spl-sentinel-loading sr-only"} "Loading more rows"]]]])

(defn end-of-list
  "Shown once every row has been loaded, so the list has a visible bottom
  rather than simply stopping."
  [column-count]
  [:tr {:class "spl-end"}
   [:td {:colspan column-count} "End of list"]])

(defn- body-rows
  "The <tr>s, the prefetch trigger and whichever of the sentinel or the end
  marker belongs at the bottom.

  The initial render and an infinite-scroll response both come through here, so
  a row appended by scrolling is built the same way as one present on load."
  [& {:keys [columns rows row-attrs next-url]}]
  (let [n (count columns)
        ;; Clamped, so a short final response still prefetches from near its top
        ;; rather than not at all.
        prefetch-idx (when next-url
                       (max 0 (- (count rows) prefetch-offset)))]
    (list
      (for [[i row] (map-indexed vector rows)]
        (list
          (when (= i prefetch-idx)
            (prefetch-row next-url n))
          [:tr (when row-attrs (row-attrs row))
           (for [col columns]
             [:td (cond-> {:class (column-classes col)}
                    (:stacked col) (assoc :data-stacked ((:stacked col) row))
                    (:attrs col) (merge ((:attrs col) row)))
              ((:cell col) row)])]))
      (if next-url
        (sentinel-row n)
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
    :stacked  — (fn [row] …) returning the one-line summary the cell shows
                below 640px, where the table collapses to a single column and
                every other cell is hidden. Belongs on the first column; that
                is the cell that survives. Without it a phone shows a list of
                bare names with nothing to tell them apart.
    :attrs    — optional (fn [row] …) returning extra attributes for this
                column's <td>.

  rows: A list of data. Each row is passed to (:cell column)
  row-attrs: Optional function (row) -> attrs map for each <tr> element.
             Use this to add click handlers, HTMX attributes, etc.

  Stays a real <table>. Layout comes from `table-layout: fixed` in the
  stylesheet, never from display:grid or display:block on a row — those drop
  the implicit ARIA roles a screen reader relies on to announce a cell's
  column.

  empty-state: shown instead of the table when there are no rows at all.

  href, page, page-size, total: the paging state. Given all four the table
  loads the next page as the reader nears the bottom; given none it renders a
  single fixed list. Derived here rather than by each caller, so every list
  agrees on when there is a next page and where its trigger sits."
  [& {:keys [columns rows row-attrs href page page-size total empty-state]}]
  (if (and empty-state (empty? rows))
    ;; In place of the table, not inside it. A header row over nothing, with
    ;; "END OF LIST" underneath, reads as a list that failed to load.
    empty-state
    [:table {:class "spl-table"}
     [:thead
      [:tr
       (for [col columns]
         [:th {:scope "col"
               :class (column-classes col)}
          (:name col)])]]
     [:tbody {:id rows-container-id}
      (body-rows :columns columns
                 :rows rows
                 :row-attrs row-attrs
                 :next-url (next-page-url :href href
                                          :page page
                                          :page-size page-size
                                          :total total))]]))

(defn card-table
  "The list surface. Rows scroll inside it; the header stays put.

  There is no pager: lists load the next page as you reach the bottom, and the
  row count lives in the toolbar beside the search. See `sentinel-row`."
  ([table]
   [:div {:class "spl-table-card"}
    [:div {:class "spl-table-scroll"} table]])
  ([table _paginator]
   (card-table table)))

(def count-id
  "The toolbar's row count. An infinite-scroll response swaps it out of band, so
  the number tracks what is actually loaded instead of freezing at the first
  page."
  "table-row-count")

(defn row-count
  "\"25 of 1,284\", for the toolbar. The count belongs beside the search that
  changes it rather than at the foot of a list you have to reach to read.

  `oob?` marks it as the out-of-band half of an infinite-scroll response."
  [& {:keys [loaded total oob?]}]
  [:p (cond-> {:id count-id :class "spl-count"}
        oob? (assoc :hx-swap-oob "true"))
   (if (or (nil? total) (zero? total))
     "No rows"
     (format "%,d of %,d" (or loaded 0) total))])

(defn rows-only
  "An infinite-scroll response: the next page's <tr>s, plus an out-of-band
  update of the toolbar count.

  htmx wraps a partial response in a <template> before parsing it, so a <p> at
  the top level survives beside the <tr>s rather than being foster-parented out
  of a table."
  [& {:keys [columns rows row-attrs href page page-size total]}]
  (list
    (body-rows :columns columns
               :rows rows
               :row-attrs row-attrs
               :next-url (next-page-url :href href
                                        :page page
                                        :page-size page-size
                                        :total total))
    (when (and page page-size total)
      (row-count :loaded (min (* page page-size) total)
                 :total total
                 :oob? true))))

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
