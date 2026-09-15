(ns sepal.app.routes.settings.codes
  (:require [clojure.string :as str]
            [failjure.core :as f]
            [sepal.accession.interface :as accession.i]
            [sepal.app.codes :as codes]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as form]
            [sepal.code-template.interface :as ct.i]
            [sepal.error.interface :as error.i]
            [sepal.settings.interface :as settings.i]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z])
  (:import [java.time LocalDate]))

(def token-help
  "{year} {year2} {month} {day} and one {seq}. Zeros after a colon set the
  width, so {seq:0000} counts 0001, 0002.")

(defn- strict-checkbox [& {:keys [name label checked? errors]}]
  (form/field
    :label label
    :name name
    :errors errors
    :input [:label {:class "spl-checkbox"}
            [:input {:type "checkbox"
                     :id name
                     :name name
                     :value "1"
                     :checked (boolean checked?)}]
            "Reject a code that does not fit"]))

(defn codes-form [& {:keys [values errors previews]}]
  (form/form
    {:method "post"
     :action (z/url-for settings.routes/codes)}
    (form/anti-forgery-field)

    [:div {:class "spl-form"}
     (form/section
       :title "Accessions"
       :hint "How an accession code is suggested on the create form."
       :children
       [(form/input-field :label "Template"
                          :name "accession_template"
                          :value (:accession_template values)
                          :errors (:accession_template errors)
                          :help (if-let [next-code (:accession previews)]
                                  (str "Next: " next-code ". " token-help)
                                  token-help))
        (strict-checkbox :name "accession_strict"
                         :label "Enforcement"
                         :checked? (:accession_strict values)
                         :errors (:accession_strict errors))])

     (form/section
       :title "Material"
       :hint "Material is numbered within its accession, so it starts again at
              one for every accession."
       :children
       [(form/input-field :label "Template"
                          :name "material_template"
                          :value (:material_template values)
                          :errors (:material_template errors)
                          :help (if-let [example (:material previews)]
                                  (str "For example: " example ". " token-help)
                                  token-help))
        (strict-checkbox :name "material_strict"
                         :label "Enforcement"
                         :checked? (:material_strict values)
                         :errors (:material_strict errors))])]

    [:div {:class "mt-4"}
     (layout/save-button "Save changes")]))

(defn render [& {:keys [viewer values errors flash previews]}]
  (layout/layout
    :viewer viewer
    :current-route settings.routes/codes
    :category "Organization"
    :title "Codes"
    :flash flash
    :content (codes-form :values values :errors errors :previews previews)))

(def FormParams
  [:map {:closed true}
   form/AntiForgeryField
   [:accession_template {:decode/form validation.i/empty->nil} [:maybe :string]]
   [:material_template {:decode/form validation.i/empty->nil} [:maybe :string]]
   ;; A cleared checkbox posts nothing at all, so both flags are optional and
   ;; absence means off.
   [:accession_strict {:optional true} [:maybe :string]]
   [:material_strict {:optional true} [:maybe :string]]])

(defn- settings->values [config]
  {:accession_template (-> config :accession :template)
   :material_template (-> config :material :template)
   :accession_strict (-> config :accession :strict?)
   :material_strict (-> config :material :strict?)})

(defn- previews
  "What each template would produce right now. The accession preview is the
  real next code; material's is an illustration, because its real answer
  depends on which accession you are adding to."
  [db config]
  (let [today (LocalDate/now)]
    {:accession (accession.i/next-code db (-> config :accession :template) today)
     :material (let [parts (ct.i/parse (-> config :material :template))]
                 (when-not (f/failed? parts)
                   (ct.i/render parts today 1)))}))

(defn- check-templates
  "The two rules malli cannot express. Returns humanized field errors, or nil."
  [{:keys [accession_template material_template] :as data}]
  (let [strict? (fn [k] (= "1" (get data k)))
        template-error (fn [template]
                         (when (some? template)
                           (let [parsed (ct.i/parse template)]
                             (when (error.i/error? parsed)
                               [(error.i/message parsed)]))))
        strict-blank ["Set a template before enforcing it"]]
    (not-empty
      (cond-> {}
        (template-error accession_template)
        (assoc :accession_template (template-error accession_template))

        (template-error material_template)
        (assoc :material_template (template-error material_template))

        (and (strict? :accession_strict) (str/blank? accession_template))
        (assoc :accession_template strict-blank)

        (and (strict? :material_strict) (str/blank? material_template))
        (assoc :material_template strict-blank)))))

(defn- ->settings [data]
  {"codes.accession_template" (:accession_template data)
   "codes.material_template" (:material_template data)
   "codes.accession_strict" (if (= "1" (:accession_strict data)) "1" "0")
   "codes.material_strict" (if (= "1" (:material_strict data)) "1" "0")})

(defn handler [{:keys [::z/context flash form-params request-method viewer]}]
  (let [{:keys [db]} context
        config (codes/config db)]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)]
        (if-let [errors (check-templates data)]
          (render :viewer viewer
                  :values {:accession_template (:accession_template data)
                           :material_template (:material_template data)
                           :accession_strict (= "1" (:accession_strict data))
                           :material_strict (= "1" (:material_strict data))}
                  :errors errors
                  :previews (previews db config))
          (f/attempt-all [_saved (f/try* (let [new-settings (->settings data)]
                                           (settings.i/set-values! db new-settings)
                                           (settings.activity/create! db
                                                                      settings.activity/updated
                                                                      (:user/id viewer)
                                                                      {:changes new-settings})
                                           new-settings))]
            (-> (http/see-other settings.routes/codes)
                (flash/success "Code settings updated successfully"))
            (f/when-failed [e]
              (http/failure-flash e (http/see-other settings.routes/codes)
                                  "Could not save the code settings"))))
        (f/when-failed [e]
          (render :viewer viewer
                  :values (settings->values config)
                  :errors (error.i/humanize e)
                  :previews (previews db config))))

      (render :viewer viewer
              :values (settings->values config)
              :flash flash
              :previews (previews db config)))))
