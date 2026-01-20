(ns sepal.app.routes.setup.taxonomy
  (:require [sepal.app.flash :as flash]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.setup.layout :as layout]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.ui.form :as form]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(defn render-taxa-exist
  "Render the view when taxa already exist in the database."
  [& {:keys [taxa-count flash-messages]}]
  (layout/layout
    :current-step 5
    :flash-messages flash-messages
    :content
    (layout/step-card
      :title "Taxonomy Data"
      :back-url (z/url-for setup.routes/regional)
      :content
      [:div {:class "space-y-4"}
       [:div {:class "alert alert-info"}
        [:div
         [:p {:class "font-medium"} "Taxonomy data already exists"]
         [:p (format "Your database already contains %,d taxa. The WFO Plant List import is only available for empty databases to avoid conflicts with existing taxonomic data."
                     taxa-count)]]]
       [:p {:class "text-base-content/70"}
        "You can continue using your existing taxa, or contact an administrator to reset the database if you want to start fresh with WFO data."]]
      :next-button
      [:a {:href (z/url-for setup.routes/review)
           :class "btn btn-primary"}
       "Continue →"])))

(defn render-import-available
  "Render the view when WFO import is available."
  [& {:keys [flash-messages]}]
  (layout/layout
    :current-step 5
    :flash-messages flash-messages
    :content
    (layout/step-card
      :title "Taxonomy Data"
      :back-url (z/url-for setup.routes/regional)
      :content
      [:div {:class "space-y-4"}
       [:p {:class "text-base-content/70"}
        "Sepal can import the World Flora Online (WFO) Plant List, a comprehensive database of plant names and their taxonomic status."]

       [:div {:class "bg-base-200 p-4 rounded-lg"}
        [:h4 {:class "font-medium mb-2"} "What you'll get:"]
        [:ul {:class "list-disc list-inside text-sm text-base-content/70 space-y-1"}
         [:li "Over 450,000 plant taxa"]
         [:li "Scientific names with authors"]
         [:li "Taxonomic hierarchy (family, genus, species)"]]]

       [:div {:class "flex gap-3 mt-4"}
        [:form {:method "post"
                :action (z/url-for setup.routes/taxonomy)
                :x-data "{ submitting: false }"
                :x-on:submit "submitting = true"}
         (form/anti-forgery-field)
         [:input {:type "hidden" :name "action" :value "import"}]
         [:button {:type "submit"
                   :class "btn btn-success"
                   :x-bind:disabled "submitting"
                   :x-bind:class "submitting && 'loading'"}
          [:span {:x-show "!submitting"} "Import WFO Plant List"]
          [:span {:x-show "submitting" :x-cloak true} "Importing... this may take a minute"]]]
        [:a {:href (z/url-for setup.routes/review)
             :class "btn btn-ghost"
             :x-show "!submitting"}
         "Skip for now"]]]
      :next-button nil)))

(defn handler [{:keys [::z/context flash request-method]}]
  (let [{:keys [db]} context
        can-import? (setup.shared/can-import-wfo? db)
        taxa-count (db.i/count db {:select [:id] :from [:taxon]})]

    (case request-method
      :post
      (if-not can-import?
        ;; Taxa exist, can't import - redirect to review
        (http/see-other setup.routes/review)
        ;; Try to import
        (let [result (setup.shared/import-wfo-taxonomy! db)]
          (if (:error result)
            (-> (http/see-other setup.routes/taxonomy)
                (flash/error (:error result)))
            (do
              (setup.shared/set-current-step! db 6)
              (-> (http/see-other setup.routes/review)
                  (flash/success (or (:message result)
                                     "WFO taxonomy imported successfully")))))))

      ;; GET
      (do
        (setup.shared/set-current-step! db 5)
        (html/render-page
          (if can-import?
            (render-import-available :flash-messages (:messages flash))
            (render-taxa-exist :taxa-count taxa-count
                               :flash-messages (:messages flash))))))))
