(ns sepal.app.routes.setup.index
  (:require [sepal.app.http-response :as http]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [zodiac.core :as z]))

;; Note: Taxonomy step (5) is hidden for now (unfinished WFO import)
(def step->route
  {1 setup.routes/admin
   2 setup.routes/server
   3 setup.routes/organization
   4 setup.routes/regional
   5 setup.routes/review})

(defn handler
  "Redirect to the current step in the setup wizard."
  [{:keys [::z/context]}]
  (let [{:keys [db]} context
        current-step (setup.shared/get-current-step db)
        route (get step->route current-step setup.routes/admin)]
    (http/found route)))
