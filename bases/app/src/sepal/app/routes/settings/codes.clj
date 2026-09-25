(ns sepal.app.routes.settings.codes
  (:require [clojure.string :as str]
            [failjure.core :as f]
            [sepal.accession.interface :as accession.i]
            [sepal.app.codes :as codes]
            [sepal.app.datetime :as datetime]
            [sepal.app.flash :as flash]
            [sepal.app.http-response :as http]
            [sepal.app.json :as json]
            [sepal.app.routes.settings.layout :as layout]
            [sepal.app.routes.settings.routes :as settings.routes]
            [sepal.app.ui.form :as form]
            [sepal.code-template.interface :as ct.i]
            [sepal.error.interface :as error.i]
            [sepal.settings.interface :as settings.i]
            [sepal.settings.interface.activity :as settings.activity]
            [sepal.validation.interface :as validation.i]
            [zodiac.core :as z]))

(def token-legend
  "Tokens: {year} {year2} {month} {day}, and exactly one {seq} or {letter}.
  Zeros after a colon set the width, so {seq:0000} counts 0001, 0002. {letter}
  counts A, B … Z, AA, AB.")

(defn- strict-checkbox
  "A bare wrapping label, like every other checkbox in the app.

  Not form/field: that emits its own <label for>, which would give the control
  two labels, an accessible name of \"Enforcement Reject a code…\", and a
  heading that toggles the box when you click it.

  Disabled while the template beside it is blank, because enforcing nothing is
  what the server refuses anyway. A disabled box posts nothing, which is
  exactly the off value."
  [& {:keys [name checked? errors]}]
  [:div {:class "spl-field"}
   [:label {:class "flex items-center gap-2 cursor-pointer"}
    ;; spl-checkbox sizes the box itself at 16px. On the wrapping label it
    ;; sizes the label, and the text wraps one word per line.
    [:input {:type "checkbox"
             :class "spl-checkbox"
             :id name
             :name name
             :value "1"
             :checked (boolean checked?)
             :x-bind:disabled "!template.trim()"}]
    [:span {:class "spl-label"} "Reject a code that does not fit"]]
   (form/error-list name errors :hx-swap-oob? true)])

(defn codes-form [& {:keys [values errors previews]}]
  (form/form
    {:method "post"
     :action (z/url-for settings.routes/codes)}
    (form/anti-forgery-field)

    ;; No spl-form wrapper: it carries 26px/30px padding that is reset only
    ;; inside a record page, so on a settings page it indents the fields 30px
    ;; further than every other settings page and past its own Save button.
    [:div
     [:div {:x-data (json/js {:template (or (:accession_template values) "")})}
      (form/section
        :title "Accessions"
        :hint (str "How an accession code is suggested on the create form. "
                   "Leave it blank to suggest nothing. " token-legend)
        :children
        [(form/input-field :label "Template"
                           :name "accession_template"
                           :value (:accession_template values)
                           :errors (:accession_template errors)
                           :input-attrs {:x-model "template"}
                           :help (when-let [next-code (:accession previews)]
                                   (str "Next: " next-code)))
         (strict-checkbox :name "accession_strict"
                          :checked? (:accession_strict values)
                          :errors (:accession_strict errors))])]

     [:div {:x-data (json/js {:template (or (:material_template values) "")})}
      (form/section
        :title "Material"
        :hint "Material is numbered within its accession, so it starts again at one for every accession. Leave it blank to suggest nothing."
        :children
        [(form/input-field :label "Template"
                           :name "material_template"
                           :value (:material_template values)
                           :errors (:material_template errors)
                           :input-attrs {:x-model "template"}
                           :help (when-let [example (:material previews)]
                                   (str "For example: " example)))
         (strict-checkbox :name "material_strict"
                          :checked? (:material_strict values)
                          :errors (:material_strict errors))
         (form/input-field :label "Separator"
                           :name "material_separator"
                           :value (:material_separator values)
                           :errors (:material_separator errors)
                           :help "Between the accession code and the material code. Leave it blank for none.")
         (when (ct.i/runs-together? (:accession_template values)
                                    (:material_separator values)
                                    (:material_template values))
           [:div {:class "spl-alert spl-alert--warning"}
            [:p (str "With no separator, a material code that starts with a digit "
                     "runs into an accession code that ends with one, and "
                     "2026.0001 and 1 read as 2026.00011.")]])])]]

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
   ;; No empty->nil: an empty separator is a garden's choice of none, and is
   ;; saved as "".
   [:material_separator {:optional true} [:maybe :string]]
   ;; A cleared checkbox posts nothing at all, so both flags are optional and
   ;; absence means off.
   [:accession_strict {:optional true} [:maybe :string]]
   [:material_strict {:optional true} [:maybe :string]]])

(defn- settings->values [config]
  {:accession_template (-> config :accession :template)
   :material_template (-> config :material :template)
   :material_separator (:separator config)
   :accession_strict (-> config :accession :strict?)
   :material_strict (-> config :material :strict?)})

(defn- previews
  "What each template would produce right now. The accession preview is the
  real next code; material's is an illustration, because its real answer
  depends on which accession you are adding to. It shows the full code, so
  the separator is visible too."
  [db config timezone]
  (let [today (datetime/today timezone)
        accession (accession.i/next-code db (-> config :accession :template) today)]
    {:accession accession
     :material (let [parts (ct.i/parse (-> config :material :template))]
                 (when-not (f/failed? parts)
                   (ct.i/full-code (:separator config) accession (ct.i/render parts today 1))))}))

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
  ;; "" rather than nil, so a cleared field is saved as a row rather than
  ;; skipped. Every codes. setting is a seeded row.
  {"codes.accession_template" (or (:accession_template data) "")
   "codes.material_template" (or (:material_template data) "")
   "codes.material_separator" (or (:material_separator data) "")
   "codes.accession_strict" (if (= "1" (:accession_strict data)) "1" "0")
   "codes.material_strict" (if (= "1" (:material_strict data)) "1" "0")})

(defn handler [{:keys [::z/context flash form-params request-method viewer]}]
  (let [{:keys [db timezone]} context
        config (codes/config db)]
    (case request-method
      :post
      (f/attempt-all [data (validation.i/validate-form-values FormParams form-params)]
        (if-let [errors (check-templates data)]
          (render :viewer viewer
                  :values {:accession_template (:accession_template data)
                           :material_template (:material_template data)
                           :material_separator (:material_separator data)
                           :accession_strict (= "1" (:accession_strict data))
                           :material_strict (= "1" (:material_strict data))}
                  :errors errors
                  :previews (previews db config timezone))
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
                  :previews (previews db config timezone))))

      (render :viewer viewer
              :values (settings->values config)
              :flash flash
              :previews (previews db config timezone)))))
