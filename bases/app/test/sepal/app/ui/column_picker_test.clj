(ns sepal.app.ui.column-picker-test
  (:require [clojure.test :refer [deftest is]]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.ui.column-picker :as column-picker])
  (:import [org.jsoup Jsoup]))

(def columns
  [{:name "Code" :key :code :priority 1}
   {:name "Provenance" :key :provenance :priority 3}
   {:name "Supplier" :key :supplier :priority 3 :hidden? true}])

(defn- parse []
  (Jsoup/parseBodyFragment
    (binding [*anti-forgery-token* "token"]
      (chassis/html (column-picker/picker :list-key :accession
                                          :columns columns
                                          :visible-keys #{:code :provenance}
                                          :action "/lists/accession/columns")))))

(deftest test-button-opens-the-popover
  (let [body (parse)
        button (.selectFirst body "button.spl-col-picker-btn")
        panel (.selectFirst body "[popover]")]
    (is (= "Columns" (.attr button "aria-label")))
    (is (= (.attr panel "id") (.attr button "popovertarget")))))

(deftest test-checkboxes
  (let [body (parse)]
    (is (.hasAttr (.selectFirst body "input[type=checkbox][disabled]") "checked")
        "a priority 1 column shows checked and can't be changed")
    (is (nil? (.selectFirst body "input[name=offered][value=code]")) "and isn't sent")
    (is (= #{"provenance" "supplier"}
           (set (map #(.attr % "value") (.select body "input[name=offered]")))))
    (is (.hasAttr (.selectFirst body "input[name=shown][value=provenance]") "checked"))
    (is (not (.hasAttr (.selectFirst body "input[name=shown][value=supplier]") "checked")))))

(deftest test-form-posts-to-the-action
  (let [form (.selectFirst (parse) "form")]
    (is (= "/lists/accession/columns" (.attr form "hx-post")))
    (is (some? (.selectFirst form "button[name=reset]")))))
