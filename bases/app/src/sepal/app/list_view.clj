(ns sepal.app.list-view
  "The one place a list handler reads the viewer and the request for its
  columns and sort. What it returns is plain data the query and the table
  take as arguments."
  (:refer-clojure :exclude [resolve])
  (:require [lambdaisland.uri :as uri]
            [sepal.app.list-columns :as list-columns]
            [sepal.app.list-sort :as list-sort]
            [sepal.app.routes.list-columns.routes :as list-columns.routes]
            [sepal.app.ui.column-picker :as column-picker]
            [sepal.app.ui.pages.list :as pages.list]
            [zodiac.core :as z]))

(defn- previous-params
  "The query of the page this request came from, when the list's own toolbar
  sent it from the same path. Nothing else carries a sort: a combobox on
  another page asking this list for options must not inherit that page's."
  [{:keys [headers uri]}]
  (when-let [current (get headers "hx-current-url")]
    (let [u (uri/parse current)]
      (when (and (= pages.list/toolbar-id (get headers "hx-trigger"))
                 (= uri (:path u)))
        (uri/query-map u {:keywordize? false})))))

(defn resolve
  "The view of `list-key` for this request: the visible columns, the sort,
  whether the user has chosen columns, and whether the sort came from the
  previous page rather than this request."
  [{:keys [query-params viewer] :as request} list-key all-columns]
  (let [overrides (get-in viewer [:user/list-columns list-key])
        previous (previous-params request)
        params (cond-> query-params previous (list-sort/carry previous))
        sort (when-not (contains? query-params "options")
               (list-sort/resolve-sort all-columns params))]
    {:list list-key
     :all-columns all-columns
     :columns (list-columns/visible-columns all-columns overrides)
     :custom? (some? overrides)
     :sort sort
     :carried? (boolean (and sort (not (contains? query-params "sort"))))}))

(defn href
  "The list's URL with its search and sort, plus `extra`, such as :page."
  [view path q & {:as extra}]
  (let [params (cond-> (merge (list-sort/sort-params (:sort view)) extra)
                 (seq q) (assoc :q q))]
    (uri/uri-str {:path path
                  :query (when (seq params) (uri/map->query-string params))})))

(defn- sort-link [view path q]
  (let [container (str "#" pages.list/list-container-id)]
    (fn [column]
      (when (:sort column)
        (let [url (href (assoc view :sort (list-sort/next-sort column (:sort view))) path q)]
          {:href url
           :hx-get url
           :hx-target container
           :hx-select container
           :hx-swap "outerHTML"
           :hx-push-url "true"})))))

(defn table-opts
  "Options for table/table and table/rows-only."
  [view path q]
  {:columns (:columns view)
   :sort (:sort view)
   :sort-link (sort-link view path q)
   :shed? (not (:custom? view))
   :min-width? (:custom? view)
   :picker (column-picker/picker :list-key (:list view)
                                 :columns (:all-columns view)
                                 :visible-keys (set (map :key (:columns view)))
                                 :action (z/url-for list-columns.routes/save
                                                    {:list (name (:list view))}))})

(defn respond
  "A full page, as the handler returns it. When the sort came from the
  previous page, it is rendered here so htmx can be told to push the URL with
  the sort, or the address bar would lose it."
  [view body path q]
  (if (:carried? view)
    (assoc-in (z/html-response body) [:headers "HX-Push-Url"] (href view path q))
    body))
