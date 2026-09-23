(ns sepal.observation.interface.search
  "Search field definitions for observations."
  (:require [sepal.observation.core :as core]
            [sepal.search.interface :as search.i]))

(defn- material-code-clause
  "A bare word matches a material subject on its accession code, or on the
  start of its full code, `<accession code>.<material code>`. So `01` doesn't
  match every material whose own code contains `01`, but `2026.0001.01` finds
  material `0122` of accession `2026.0001`."
  [word]
  [:or
   [:like :acc.code (str "%" word "%")]
   [:like [:|| :acc.code "." :m.code] (str word "%")]])

(defn- location-clause
  "A bare word matches a location subject on its code or its name."
  [word]
  [:or
   [:like :l.code (str "%" word "%")]
   [:like :l.name (str "%" word "%")]])

;; :code and :location read acc, m and l with no :joins of their own. Both
;; callers, the index and the export, left-join all three in their base
;; statement, and a subject is only ever one of material or location.
(defmethod search.i/search-config :observation [_]
  {:table [:observation :o]
   :fields
   {:code {:column :acc.code
           :type :text
           :search? true
           :search-clause material-code-clause
           :label "Code"}

    :location {:column :l.name
               :type :text
               :search? true
               :search-clause location-clause
               :label "Location"}

    ;; type and value are :text, not :enum: both vocabularies live in
    ;; curator-editable lookup tables, so a literal list written into this
    ;; config would go stale the first time a garden adds a value.
    :type {:column :o.type
           :type :text
           :label "Type"}

    :value {:column :o.value
            :type :text
            :label "Value"}

    :observed {:column :o.observed_on
               :type :date
               :label "Observed"}

    :observer {:column :o.observed_by
               :type :text
               :label "Observed by"}

    ;; overdue:<date> is what the index's checkbox applies: due by that date
    ;; and not followed up. due: compares the date alone.
    :overdue {:column :o.next_check_on
              :type :date
              :filter-clause (fn [{:keys [value]}] (core/overdue value))
              :label "Overdue on"}

    :due {:column :o.next_check_on
          :type :date
          :label "Next check"}

    ;; subject is :enum, unlike type and value: its list is closed by
    ;; the spec.
    :subject {:column :o.resource_type
              :type :enum
              :values [:material :location]
              :label "Subject"}

    :id {:column :o.id
         :type :id
         :label "ID"}}})
