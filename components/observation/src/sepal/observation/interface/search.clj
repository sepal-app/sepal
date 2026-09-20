(ns sepal.observation.interface.search
  "Search field definitions for observations."
  (:require [sepal.search.interface :as search.i]))

(defmethod search.i/search-config :observation [_]
  {:table [:observation :o]
   :fields
   {;; type and value are :text, not :enum: both vocabularies live in
    ;; curator-editable lookup tables, so a literal list written into this
    ;; config would go stale the first time a garden adds a value.
    :type {:column :o.type
           :type :text
           :label "Type"}

    :value {:column :o.value
            :type :text
            :label "Value"}

    :observed-on {:column :o.observed_on
                  :type :date
                  :label "Observed"}

    :observed-by {:column :o.observed_by
                  :type :text
                  :label "Observed by"}

    :next-check-on {:column :o.next_check_on
                    :type :date
                    :label "Next check"}

    ;; resource-type is :enum, unlike type and value: its list is closed by
    ;; the spec.
    :resource-type {:column :o.resource_type
                    :type :enum
                    :values [:material :location]
                    :label "Subject"}

    :id {:column :o.id
         :type :id
         :label "ID"}}})
