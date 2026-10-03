(ns sepal.app.routes.media.index
  (:require [sepal.app.html :as html]
            [sepal.app.params :as params]
            [sepal.app.routes.media.routes :as media.routes]
            [sepal.app.ui.media :as media.ui]
            [sepal.app.ui.page :as ui.page]
            [sepal.app.ui.pages.list :as pages.list]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]
            [sepal.i18n.interface :refer [N_ tr trc]]
            [sepal.media.interface.search]
            [sepal.search.interface :as search.i]
            [zodiac.core :as z]))

(def ^:private sort-orders
  "Each ends on the id, so offset pages can't repeat or skip items that tie on
  the column before it."
  {:newest [[:md.created_at :desc] [:md.id :desc]]
   :oldest [[:md.created_at :asc] [:md.id :asc]]
   :title [[[:lower :md.title] :asc] [:md.id :asc]]
   :largest [[:md.size_in_bytes :desc] [:md.id :desc]]})

(def ^:private sort-options
  [[:newest (N_ "Newest first")]
   [:oldest (N_ "Oldest first")]
   [:title (N_ "Title")]
   [:largest (N_ "Largest first")]])

(defn title-buttons []
  (media.ui/upload-button))

(defn- next-page-url
  "The next page's tiles alone, with the same search and sort, or nil when this
  page was the last."
  [& {:keys [media page page-size q order]}]
  (when (>= (count media) page-size)
    (z/url-for media.routes/index nil
               (cond-> {:page (inc page) :sort (name order) :rows 1}
                 (seq q) (assoc :q q)))))

(defn- sort-select [order]
  [:label {:class "ml-4 flex items-center gap-2 text-sm"}
   [:span (tr "Sort")]
   [:select {:name "sort"
             :class "spl-input spl-select"}
    (for [[value label] sort-options]
      [:option {:value (name value)
                :selected (when (= value order) "selected")}
       (tr label)])]])

(defn page-content [& {:keys [media next-page-url page page-size q order total]}]
  (list
    [:link {:rel "stylesheet"
            :href (html/static-url "app/routes/media/css/media.css")}]
    ;; Outside the list container, which a search replaces, so the uploader is
    ;; set up once per page load.
    (media.ui/uploader)
    (pages.list/page-content
      :table-actions (pages.list/toolbar
                       :q q
                       :fields (search.i/field-options :media)
                       ;; i18n: Keep "linked:none" in English; it is search syntax
                       :placeholder (tr "Search... (e.g., linked:none)")
                       :filters (sort-select order)
                       :page page
                       :page-size page-size
                       :total total)
      ;; The list container clips its overflow, so the grid scrolls in here.
      ;; A grid rather than a table, so it keeps the page gutter.
      :content [:div {:class "spl-page-body overflow-y-auto"}
                (media.ui/media-list :media media
                                     :next-page-url next-page-url
                                     :searching? (seq q))])))

(defn render [& {:as opts}]
  (ui.page/page :content (page-content opts)
                :breadcrumbs [(trc "navigation" "Media")]
                :page-title-buttons (title-buttons)))

(def Params
  [:map
   [:page {:default 1} :int]
   [:page-size {:default 20} :int]
   [:q :string]
   [:sort {:default :newest} [:enum :newest :oldest :title :largest]]])

(defn handler [& {:keys [::z/context query-params] :as _request}]
  (let [{:keys [db timezone]} context
        {:keys [page page-size q] :as decoded} (params/decode Params query-params)
        order (let [k (keyword (:sort decoded))]
                (if (contains? sort-orders k) k :newest))
        stmt (search.i/compile-query :media
                                     (search.i/parse q)
                                     {:select [:md.*] :from [[:media :md]]}
                                     {:timezone timezone})
        total (db.i/count-bounded db stmt)
        media (->> (db.i/execute-bounded! db (assoc stmt
                                                    :limit page-size
                                                    :offset (* page-size (dec page))
                                                    :order-by (sort-orders order)))
                   (mapv #(assoc % :thumbnail-url (media.ui/thumbnail-url (:media/id %)))))
        next-url (next-page-url :media media :page page :page-size page-size
                                :q q :order order)]
    ;; Infinite scroll asks for the next page's tiles alone, with the toolbar's
    ;; count out of band. Every other request, including the toolbar's search,
    ;; gets the page.
    (if (some? (get query-params "rows"))
      (html/render-partial (list (media.ui/media-list-items :media media
                                                            :next-page-url next-url)
                                 (table/row-count :loaded (min (* page page-size) total)
                                                  :total total
                                                  :oob? true)))
      (render :media media
              :next-page-url next-url
              :page page
              :page-size page-size
              :q q
              :order order
              :total total))))
