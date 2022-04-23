(ns sepal.app.routes.org.detail)

(defn handler [{:keys []}]
  {:status 200
   :headers {"content-type" "text/html"}
   :body "detail"})
