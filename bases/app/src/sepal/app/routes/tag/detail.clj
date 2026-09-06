(ns sepal.app.routes.tag.detail
  (:require [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.json :as json]
            [sepal.app.routes.tag.form :as tag.form]
            [sepal.app.routes.tag.routes :as tag.routes]
            [sepal.app.ui.form :as ui.form]
            [sepal.app.ui.icons.heroicons :as heroicons]
            [sepal.app.ui.page :as page]
            [sepal.error.interface :as error.i]
            [sepal.tag.interface :as tag.i]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]
   [:description {:decode/form validation.i/empty->nil} [:maybe :string]]])

(defn- delete-button [tag]
  [:button {:type "button"
            :class "spl-btn spl-btn--sm spl-btn--icon spl-btn--danger"
            :aria-label "Delete tag"
            :hx-headers (json/js {"X-CSRF-Token" *anti-forgery-token*})
            :hx-delete (z/url-for tag.routes/detail {:id (:tag/id tag)})
            :hx-confirm (str "Delete tag \"" (:tag/name tag) "\"? This removes it from every resource it's linked to.")}
   (heroicons/outline-trash :class "size-4")])

(defn render [& {:keys [errors tag values]}]
  (page/page
    :content [:div {:class "max-w-2xl mx-auto"}
              (tag.form/form :action (z/url-for tag.routes/detail {:id (:tag/id tag)})
                             :errors errors
                             :values values)
              (delete-button tag)]
    :footer (ui.form/footer :buttons (tag.form/footer-buttons))
    :breadcrumbs [[:a {:href (z/url-for tag.routes/index)} "Tags"] (:tag/name tag)]))

(defn handler [{:keys [::z/context form-params request-method]}]
  (let [{:keys [db resource]} context
        id (:tag/id resource)
        values {:name (:tag/name resource) :description (:tag/description resource)}]
    (case request-method
      :post
      (let [result (validation.i/validate-form-values FormParams form-params)]
        (if (error.i/error? result)
          (http/validation-errors (validation.i/humanize result))
          (let [saved (tag.i/update! db id result)]
            (if (error.i/error? saved)
              (http/validation-errors (validation.i/humanize saved))
              (-> (http/hx-redirect tag.routes/index)
                  (flash/success "Tag updated successfully"))))))

      :delete
      (do (tag.i/delete! db id)
          (http/hx-redirect tag.routes/index))

      (render :tag resource :values values))))
