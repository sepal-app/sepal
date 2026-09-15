(ns sepal.app.codes
  "The two-line composition between the settings rows and the pure
  code-template component, so four handlers do not each repeat it.

  Settings are read here and the template is passed down, which is what keeps
  `sepal.code-template.interface` free of database access."
  (:require [sepal.app.http-response :as http]
            [sepal.app.ui.form :as ui.form]
            [sepal.code-template.interface :as ct.i]
            [sepal.error.interface :as error.i]
            [sepal.settings.interface :as settings.i]))

(defn config
  "`{:accession {:template .. :strict? ..} :material {..}}` for this garden."
  [db]
  (ct.i/config (settings.i/get-values db "codes")))

(defn accession [db]
  (:accession (config db)))

(defn material [db]
  (:material (config db)))

(defn shape-ok?
  "Does `code` fit `template`? True when there is nothing to enforce -- a blank
  or unparseable template is a setting the garden can fix, not a reason to
  refuse a save."
  [template code]
  (let [parts (ct.i/parse template)]
    (or (error.i/error? parts)
        (ct.i/matches? parts code))))

(defn rejects?
  "Should strict enforcement refuse `code`?"
  [{:keys [template strict?]} code]
  (boolean (and strict? (not (shape-ok? template code)))))

(defn shape-error
  "The field error a refused code gets. `example` is a code that would fit, so
  the message shows the convention rather than describing it."
  [example]
  {:code [(if example
            (str "Code must look like " example)
            "Code does not match the garden's template")]})

(def confirm-target-id "code-confirm")

(defn confirm-slot
  "The empty target the confirmation swaps into. Lives inside the form, so the
  checkbox it receives is posted with the next submit."
  []
  [:div {:id confirm-target-id}])

(defn confirm-swap
  "What an edit gets when strict enforcement would refuse the code: a warning
  and a tickbox, rather than a refusal.

  Edit is a speed bump and not a wall because the old data is real. A code
  that predates any template a garden set is a legitimate thing to keep, and
  the only other escape hatch would be turning the convention off for
  everyone."
  [example]
  [:div {:id confirm-target-id
         :hx-swap-oob "true"
         :class "spl-alert spl-alert--warning"}
   [:p (if example
         (str "This code does not match the garden's template, which would give "
              example ".")
         "This code does not match the garden's template.")]
   [:label {:class "spl-checkbox"}
    [:input {:type "checkbox" :name "code-override" :value "1"}]
    "Save it anyway"]])

(defn unique-violation?
  "Did this failure come from one of the code unique indexes?

  A suggestion is only as good as the constraint behind it: two people
  creating a record in the same minute both read the same next number."
  [e]
  (boolean
    (and (instance? Exception e)
         (some-> (ex-message e) (->> (re-find #"UNIQUE constraint failed"))))))

(defn taken-response
  "422 naming the code that is taken, with the field re-suggested.

  No automatic retry: saving the record under a different code than the one on
  screen is worse than asking."
  [code suggestion input]
  (http/unprocessable-entity
    [:div
     (ui.form/error-list "code" [(str code " is already taken")] :hx-swap-oob? true)
     (when suggestion
       (assoc-in input [1 :hx-swap-oob] "true"))]))
