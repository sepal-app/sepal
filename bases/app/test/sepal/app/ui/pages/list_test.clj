(ns sepal.app.ui.pages.list-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.pages.list :as pages.list])
  (:import [org.jsoup Jsoup]))

(defn- toolbar-form [hiccup]
  (-> (Jsoup/parseBodyFragment (chassis/html hiccup))
      (.selectFirst (str "form#" pages.list/toolbar-id))))

(deftest test-toolbar-form-sends-submits-through-htmx
  ;; Enter in the search box and the filter controls' requestSubmit() submit
  ;; the form. Through htmx the request carries HX-Trigger, which keeps the sort.
  (doseq [[label page] [["page-content" (pages.list/page-content :table-actions [:input {:id "q" :name "q"}])]
                        ["page-content-with-panel" (pages.list/page-content-with-panel :table-actions [:input {:id "q" :name "q"}])]]]
    (testing label
      (let [form (toolbar-form page)
            triggers (set (map str/trim (str/split (.attr form "hx-trigger") #",")))]
        (is (contains? triggers "submit"))
        (is (contains? triggers "change[target.type!='search']")
            "blurring the search box after an edit does not send a request")
        (is (not (contains? triggers "change")))
        (is (= "this:replace" (.attr form "hx-sync"))
            "an Enter's keyup and its submit do not both land")))))
