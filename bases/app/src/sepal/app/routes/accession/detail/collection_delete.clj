(ns sepal.app.routes.accession.detail.collection-delete
  "Clearing an accession's collection: \"this accession is not wild-collected
  after all\". The Collection tab could only create or update before, so that
  sentence had no answer.

  Gated on accession edit rather than a collection permission. A collection is
  a field group on an accession, not a record with its own screens, and
  clearing it is an edit of the accession."
  (:require [sepal.app.delete :as app.delete]
            [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.accession.routes :as accession.routes]
            [sepal.app.ui.delete :as ui.delete]
            [sepal.collection.interface :as coll.i]
            [sepal.error.interface :as error.i]
            [zodiac.core :as z]))

(def resource-type :collection)

(defn- render-dialog [db accession collection]
  (html/render-partial
    (ui.delete/dialog
      :action (z/url-for accession.routes/detail-collection-delete
                         {:id (:accession/id accession)})
      :label (str "the collection data on accession " (:accession/code accession))
      :blockers (app.delete/blockers resource-type db collection))))

(defn handler [{:keys [::z/context request-method viewer]}]
  (let [{:keys [db resource]} context
        collection (coll.i/get-by-accession-id db (:accession/id resource))]
    (if (nil? collection)
      (http/not-found)
      (case request-method
        :post
        (let [result (app.delete/delete! resource-type db collection (:user/id viewer))]
          (if (error.i/error? result)
            (assoc (render-dialog db resource collection) :status 422)
            (-> (http/see-other accession.routes/detail-collection
                                {:id (:accession/id resource)})
                (flash/success "Cleared the collection data."))))

        (render-dialog db resource collection)))))
