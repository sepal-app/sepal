(ns sepal.app.byte-sizes-test
  "File sizes read in the viewer's language: 1,5 MB in Spanish, not 1.5 MB."
  (:require [clojure.test :refer [deftest is]]
            [sepal.app.routes.settings.backups.index :as backups]
            [sepal.app.ui.media :as media.ui]
            [sepal.i18n.interface :as i18n]))

(deftest test-backup-sizes-use-the-viewers-decimal-mark
  (is (= "1.5 KB" (#'backups/format-bytes 1536)))
  (binding [i18n/*locale* "es"]
    (is (= "1,5 KB" (#'backups/format-bytes 1536)))
    (is (= "2,5 MB" (#'backups/format-bytes (* 2.5 1024 1024))))))

(deftest test-media-sizes-use-the-viewers-decimal-mark
  (is (= "2.5 MB" (media.ui/format-size (* 2.5 1024 1024))))
  (binding [i18n/*locale* "es"]
    (is (= "2,5 MB" (media.ui/format-size (* 2.5 1024 1024))))))
