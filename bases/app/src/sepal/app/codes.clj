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
  [:div {:id confirm-target-id :class "spl-code-confirm"}])

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
         :class "spl-code-confirm spl-alert spl-alert--warning"}
   ;; spl-alert lays its children out in a row, so the warning and the tickbox
   ;; go inside one column rather than side by side. spl-checkbox sizes the box
   ;; itself at 16px; on the wrapping label it sizes the label, and the text
   ;; wraps one word per line.
   [:div {:class "flex flex-col gap-2"}
    [:p (if example
          (str "This code does not match the garden's template. "
               "A code that fits looks like " example ".")
          "This code does not match the garden's template.")]
    [:label {:class "flex items-center gap-2 cursor-pointer"}
     [:input {:type "checkbox"
              :class "spl-checkbox"
              :name "code-override"
              :value "1"}]
     [:span "Save it anyway"]]]])

(defn unique-violation?
  "Did this failure come from one of the code unique indexes?

  A suggestion is only as good as the constraint behind it: two people
  creating a record in the same minute both read the same next number."
  [e]
  (boolean
    (and (instance? Exception e)
         (some-> (ex-message e) (->> (re-find #"UNIQUE constraint failed"))))))

(defn taken-response
  "422 naming the code that is taken, leaving the field as it was submitted.

  The value is deliberately not replaced with the next suggestion. A failed
  save must not quietly change what is on screen -- the refresh control beside
  the field is how you ask for another number, and asking is a decision you
  make rather than one the form makes for you.

  `input-fn` takes the errors and returns the Code control, so the swapped-in
  field carries the aria-invalid and aria-describedby the field it replaces
  had. Without that the one field in error is the only one with no error
  styling."
  [code input-fn]
  (let [errors [(str code " is already taken")]]
    (http/unprocessable-entity
      [:div
       (ui.form/error-list "code" errors :hx-swap-oob? true)
       (assoc-in (input-fn errors) [1 :hx-swap-oob] "true")])))
