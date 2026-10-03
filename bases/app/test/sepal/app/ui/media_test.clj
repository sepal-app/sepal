(ns sepal.app.ui.media-test
  (:require [clojure.test :refer [deftest is]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.media :as media.ui])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup]
  (Jsoup/parseBodyFragment (chassis/html hiccup)))

(deftest test-the-sort-is-an-icon-with-a-tooltip
  (let [body (parse (media.ui/sort-select :title))
        select (.selectFirst body "select[name=sort]")
        tip (.selectFirst body ".spl-tip-host > .spl-tip")]
    (is (= "Sort" (.attr select "aria-label"))
        "with no visible label, the select names itself")
    (is (some? (.selectFirst body ".spl-tip-host > svg")) "the icon hosts the tip")
    (is (= "Sort" (.text tip)))
    (is (= "title" (.attr (.selectFirst select "option[selected]") "value")))
    (is (= ["newest" "oldest" "title" "largest"]
           (map #(.attr % "value") (.select select "option"))))
    (.remove select)
    (is (= "Sort" (.text body)) "outside the select, the tip is the only text")))

(deftest test-a-tab-form-carries-the-sort-and-the-scope
  (let [body (parse (media.ui/tab-filters :action "/taxon/1/media/"
                                          :order :oldest
                                          :below? true
                                          :hint "Media linked below"))
        form (.selectFirst body "form[method=get]")]
    (is (= "/taxon/1/media/" (.attr form "action")))
    (is (some? (.selectFirst form "input[name=scope][checked]")))
    (is (= "oldest" (.attr (.selectFirst form "select[name=sort] option[selected]") "value")))
    (is (re-find #"requestSubmit" (.attr (.selectFirst form "select[name=sort]") "onchange"))
        "changing the sort reloads the tab, as the checkbox does")))

(deftest test-a-tab-without-a-scope-still-sorts
  (let [body (parse (media.ui/tab-filters :action "/material/1/media/"
                                          :order :newest))]
    (is (some? (.selectFirst body "form select[name=sort]")))
    (is (nil? (.selectFirst body "input[name=scope]")))))
