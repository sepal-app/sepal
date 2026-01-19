(ns sepal.app.routes.setup.spec
  "Setup wizard step definitions.")

;; Steps in the setup wizard
;; Note: Taxonomy step is hidden for now (unfinished WFO import)
(def steps
  [{:id 1 :name "Account" :key :admin}
   {:id 2 :name "Server" :key :server}
   {:id 3 :name "Organization" :key :organization}
   {:id 4 :name "Regional" :key :regional}
   {:id 5 :name "Review" :key :review}])

(def total-steps (count steps))

(defn step-by-id [id]
  (first (filter #(= (:id %) id) steps)))
