(ns sepal.app.routes.taxon.index
  (:require [lambdaisland.uri :as uri]
            [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.json :as json]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as page]
            [sepal.app.ui.table :as table]
            [sepal.database.interface :as db.i]))

(def default-page-size 25)

(defn search-field [q]
  [:div {:class "flex flex-row"}
   [:input {:name "q"
            :class "spl-input w-96"
            :type "search"
            :value q
            :placeholder "Search..."}]
   [:button
    {:type "button",
     :class (html/attr "inline-flex" "items-center" "mx-2" "px-2.5" "py-1.5" "border"
                       "border-gray-300" "shadow-sm" "text-xs" "font-medium" "rounded"
                       "text-gray-700" "bg-white" "hover:bg-gray-50" "focus:outline-none"
                       "focus:ring-2" "focus:ring-offset-2" "focus:ring-indigo-500")
     :onclick "document.getElementById('q').value = null; this.form.submit()"}
    (heroicons/outline-x :size 20)]])

(defn list-page-content [& {:keys [content table-actions]}]
  [:div
   [:form {:method "get"}
    [:div
     {:class "flex justify-between mt-8"}
     [:div
      {:class "flex flex-row"}
      table-actions]]
    [:div
     {:class "mt-4 flex flex-col"}
     [:div
      {:class "-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8"}
      [:div
       {:class "inline-block min-w-full py-2 align-middle md:px-6 lg:px-8"}
       [:div
        {:class "overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg"}
        content]]]]]])

(defn create-button [& {:keys [org router]}]
  [:a {:class (html/attr "inline-flex" "items-center" "justify-center" "rounded-md"
                         "border" "border-transparent" "bg-green-700" "px-4" "py-2"
                         "text-sm" "font-medium" "text-white" "shadow-sm" "hover:bg-green-700"
                         "focus:outline-none" "focus:ring-2" "focus:ring-grenn-500"
                         "focus:ring-offset-2" "sm:w-auto")
       :href (url-for router :org/taxa-new {:org-id (:organization/id org)})}
   "Create"])

(defn table-columns [router]
  [{:name "Name"
    :cell (fn [t] [:a {:href (url-for router
                                      :taxon/detail
                                      {:id (:taxon/id t)})
                       :class "spl-link"}
                   (:taxon/name t)])}
   {:name "Rank"
    :cell :taxon/rank}])

(defn table [& {:keys [rows page-num href page-size router total]}]
  [:div {:class "w-full"}
   (table/card-table
    (table/table :columns (table-columns router)
                 :rows rows)
    (table/paginator :current-page page-num
                     :href href
                     :page-size page-size
                     :total total))])

(defn page-content [& {:keys [href page-num page-size router rows total]}]
  [:div
   (list-page-content :action ""
                      :content (table :href href
                                      :page-num page-num
                                      :page-size page-size
                                      :router router
                                      :rows rows
                                      :total total)
                      :table-actions (search-field (-> href uri/query-map :q)))])


(defn handler [& {:keys [context headers query-params ::r/router uri]}]
  (let [{:keys [db]} context
        org (:current-organization context)
        ;; TODO: validate page and page size
        {:strs [page page-size q]
         :or {page-size default-page-size}} query-params
        page-num (or (when page (Integer/parseInt page)) 1)
        offset (* page-size (- page-num 1))
        stmt {:select [:t.*]
              :from [[:public.taxon :t]]
              :where [:and
                      [:= :organization_id (:organization/id org)]
                      (if q
                        [:ilike :name (format "%%%s%%" q)]
                        :true)]}
        total (db.i/count db stmt)
        rows (db.i/execute! db (assoc stmt
                                      :limit page-size
                                      :offset offset
                                      :order-by [:name]) )]

    (if (= (get headers "accept") "application/json")
      (json/json-response (for [taxon rows]
                       {:name (:taxon/name taxon)
                        :id (:taxon/id taxon)
                        :author (:taxon/author taxon)}))
      (-> (page/page :router router
                     :page-title "Taxa"
                     :page-title-buttons (create-button :router router
                                                        :org org)
                     :content (page-content :rows rows
                                            :page-num page-num
                                            :page-size page-size
                                            :router router
                                            :href (uri/uri-str {:path uri
                                                                :query (uri/map->query-string query-params)})
                                            :total total))
          (html/render-html)))))
