(ns sepal.app.routes.org.create
  (:require [next.jdbc.types :as jdbc.types]
            [reitit.core :as r]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.router :refer [url-for]]
            [sepal.app.ui.form :as form]
            [sepal.app.ui.page :as page]
            [sepal.database.interface :as db.i]
            [sepal.error.interface :as error.i]
            [sepal.organization.interface :as org.i]
            [sepal.organization.interface.activity :as org.activity]
            [sepal.validation.interface :refer [error?]]))

(defn form [& {:keys [router values]}]
  [:form {:method "post"
          :action (url-for router :org/create)}
   (form/anti-forgery-field)
   (form/input-field :label "Organization name" :name "name" (:name values))
   (form/input-field :label "Short name" :name "short-name" (:short-name values))
   (form/input-field :label "Abbreviation" :name "abbreviation" (:abbreviation values))
   [:div {:class "flex flex-row mt-4 justify-between items-center"}
    [:button {:type "submit"
              :class (html/attr "inline-flex" "justify-center" "py-2" "px-4" "border"
                                "border-transparent" "shadow-sm" "text-sm" "font-medium"
                                "rounded-md" "text-white" "bg-green-700" "hover:bg-green-700"
                                "focus:outline-none" "focus:ring-2" "focus:ring-offset-2"
                                "focus:ring-green-500")}
     "Create organization"]]])

(defn render [& {:keys [router values]}]
  (-> (page/page :router router
                 :content (form :router router
                                :values values))
      (html/render-html)))

(defn create! [db created-by data]
  (db.i/with-transaction [tx db]
    (let [result (org.i/create! tx data)]
      (when-not (error.i/error? result)
        (org.activity/create! tx org.activity/created created-by result))
      result)))

(defn handler [{:keys [context params request-method viewer ::r/router viewer]}]
  (let [{:keys [db]} context]
    (case request-method
      :post
      (let [data (-> params
                     (select-keys [:name :short-name :abbreviation]))
            ;; TODO: create the user and assign the role in the same transaction
            result (create! db (:user/id viewer) data)
            _ou (when-not (error? result) (org.i/assign-role db
                                                             {:organization-id (:organization/id result)
                                                              :user-id (:user/id viewer)
                                                              :role (jdbc.types/as-other "owner")}))]
        (if-not (error? result)
          (http/found router :org/detail {:org-id (-> result :organization/id str)})
          (-> (http/see-other router :org/create)
              (flash/set-field-errors result)
              (assoc-in [:flash :values] data))))

      ;; else
      (render :router router
              :values params))))
