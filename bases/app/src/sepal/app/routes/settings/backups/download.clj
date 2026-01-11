(ns sepal.app.routes.settings.backups.download
  (:require [clojure.java.io :as io]
            [sepal.app.backup.core :as backup]
            [sepal.app.http-response :as http]
            [zodiac.core :as z]))

(defn handler [{:keys [path-params ::z/context]}]
  (let [{:keys [db]} context
        {:keys [filename]} path-params
        config (backup/get-config db)
        file (backup/get-backup-file (:path config) filename)]
    (if file
      {:status 200
       :headers {"Content-Type" "application/zip"
                 "Content-Disposition" (str "attachment; filename=\"" filename "\"")}
       :body (io/input-stream file)}
      (http/not-found))))
