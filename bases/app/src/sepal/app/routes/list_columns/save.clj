(ns sepal.app.routes.list-columns.save
  "Saves the viewer's column choices for one list, then has htmx reload it."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [lambdaisland.uri :as uri]
            [sepal.app.features :as features]
            [sepal.app.list-columns :as list-columns]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.user.interface :as user.i]
            [zodiac.core :as z]))

(defn- as-vector [v]
  (cond
    (nil? v) []
    (string? v) [v]
    :else (vec v)))

(defn- local-path
  "`path` when it stays on this host, else \"/\". A browser reads \"//host\"
  and \"/\\host\" in a Location as another origin."
  [path]
  (if (and (string? path)
           (str/starts-with? path "/")
           (not (#{\/ \\} (get path 1))))
    path
    "/"))

(defn- list-url
  "The list the picker sits on, as a path and query: paging dropped, and the
  sort dropped when its column is now hidden or `reset?`. The host is
  discarded."
  [current-url hidden-keys reset?]
  (let [u (uri/parse current-url)
        query (uri/query-map u {:keywordize? false})
        params (cond-> (dissoc query "page" "rows")
                 (or reset? (contains? hidden-keys (get query "sort"))) (dissoc "sort" "dir"))]
    (uri/uri-str {:path (local-path (:path u))
                  :query (when (seq params) (uri/map->query-string params))})))

(defn handler [{:keys [::z/context form-params headers path-params viewer]}]
  (let [{:keys [db]} context
        list-key (keyword (:list path-params))
        offered (as-vector (get form-params "offered"))
        shown (set (as-vector (get form-params "shown")))
        reset? (some? (get form-params "reset"))]
    (cond
      (not (contains? list-columns/lists list-key))
      {:status 404 :body ""}

      (not (features/list-enabled? list-key))
      {:status 404 :body ""}

      (not (list-columns/valid-keys? (concat offered shown)))
      {:status 400 :body ""}

      :else
      (let [overrides (when-not reset?
                        (into {} (map (fn [k] [(keyword k) (contains? shown k)])) offered))
            hidden (if reset? #{} (set (remove shown offered)))
            container (str "#" pages.list/list-container-id)]
        (user.i/set-list-columns! db (:user/id viewer) list-key overrides)
        (if-let [current (get headers "hx-current-url")]
          {:status 200
           :headers {"HX-Location" (json/write-str {:path (list-url current hidden reset?)
                                                    :target container
                                                    :select container
                                                    :swap "outerHTML"})}
           :body ""}
          ;; Without htmx there is no popover to have posted from; go back.
          {:status 303
           :headers {"Location" (local-path (some-> (get headers "referer") uri/parse :path))}
           :body ""})))))
