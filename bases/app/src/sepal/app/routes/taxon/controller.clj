(ns sepal.app.routes.taxon.controller
  (:require [reitit.core :as r]
            [sepal.app.html :as html]
            [sepal.app.http-response :refer [->path found see-other]]
            [sepal.app.routes.taxon.views.index :as index]
            [sepal.app.routes.taxon.views.new :as new]
            [sepal.app.routes.taxon.views.show :as show]
            [sepal.taxon.interface :as taxon.i]
            [clojure.walk :as walk]))

(defn index-handler [{:keys [context ::r/router session viewer]}]
  (let [{:keys [db]} context
        org (:organization session)
        ;; TODO: apply query, page, sort order, etc
        taxa (taxon.i/query db :where [:= :organization_id (:organization/id org)])]
    (-> (index/render :org org :router router :taxa taxa :user viewer)
        (html/render-html))))

(defn new-handler [{:keys [params ::r/router session viewer]}]
  (let [org (:organization session)]
    (-> (new/render :org org :router router :user viewer :form-values params)
        (html/render-html))))

(defn create-handler [{:keys [context params path-params ::r/router session]}]
  (let [{:keys [db]} context
        org (:organization session)]
    (let [data (-> params
                   (select-keys [:name :rank :parent-id])
                   (assoc ;; :created-by (:user/id viewer)
                    :organization-id (-> path-params :org-id Integer/parseInt)))
          taxon (taxon.i/create! db data)
          error (:error taxon)]
      (if-not error
        (see-other router :taxon/detail {:org-id (-> org :organization/id str)
                                         :id (:taxon/id taxon)})
        (-> (found router :taxon/new)
            (assoc :flash {:error error
                           :values data}))))))

(defn show-handler [{:keys [context path-params ::r/router session viewer]}]
  (let [{:keys [db]} context
        org (:organization session)
        taxon-id (-> path-params :id Integer/parseInt)
        ;; TODO: create a middleware to validate the viewer is a member of the
        ;; organization of the taxon...if we did that we could probably even
        ;; drop the organization membership check
        ;; taxon (-> (taxon.i/query db :where [:and
        ;;                                     [:= :id taxon-id]
        ;;                                     [:= :organization_id org]])
        ;;           first)
        taxon (taxon.i/get-by-id db (-> path-params :id Integer/parseInt))
        ;; We stringify and then keywordize to remove the namespaces keys from taxon
        form-values (-> taxon
                        (walk/stringify-keys)
                        (walk/keywordize-keys))]
    (-> (show/render :org org :router router :user viewer :form-values form-values)
        (html/render-html))))

(defn update-handler [{:keys [context params path-params ::r/router session viewer]}]
  (let [{:keys [db]} context
        org (:organization session)
        taxon-id (-> path-params :id Integer/parseInt)
        taxon (taxon.i/update! db taxon-id params)
        error (:error taxon)]
    (if-not error
      (found router :taxon/detail {:org-id (-> org :organization/id str)
                                   :id (:taxon/id taxon)})
      (-> (found router :taxon/detail)
          (assoc :flash {:error error
                         :values params})))
    (-> (show/render :org org :router router :user viewer :form-values params)
        (html/render-html))))

(defn destroy-handler [{:keys []}]
  nil)
