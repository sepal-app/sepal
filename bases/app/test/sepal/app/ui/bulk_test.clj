(ns sepal.app.ui.bulk-test
  (:require [clojure.test :refer [deftest is]]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.ui.bulk :as ui.bulk])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup] (Jsoup/parseBodyFragment (chassis/html hiccup)))

(deftest test-scope-attrs
  (let [attrs (ui.bulk/scope-attrs :toolbar-id "list-toolbar" :list-container-id "list-container")]
    (is (= "listSelection('list-toolbar')" (:x-data attrs)))
    (is (re-find #"'list-container'" (:x-on:htmx:before-request attrs)))
    (is (= "applied()" (:x-on:bulk-applied attrs)))))

(deftest test-dialog
  (let [body (binding [*anti-forgery-token* "token"]
               (parse (ui.bulk/dialog :id "bulk-status" :title "Change status"
                                      :action "/material/bulk/status/"
                                      :confirm-one "Change %1 material"
                                      :confirm-other "Change %1 materials"
                                      :body [:p "fields"])))
        form (.selectFirst body "dialog#bulk-status form")]
    (is (= "/material/bulk/status/" (.attr form "hx-post")))
    (is (= "none" (.attr form "hx-swap")))
    (is (re-find #"X-CSRF-Token" (.attr form "hx-headers")))
    (is (some? (.selectFirst form "template[x-for] input[type=hidden][name=ids]")))
    (is (some? (.selectFirst form "button[type=submit][x-text]")))))

(deftest test-action-bar
  (let [body (parse (ui.bulk/action-bar :actions [:button "Move"]))]
    (is (= "selected.length > 0" (.attr (.selectFirst body ".spl-bulk-bar") "x-show")))
    (is (some? (.selectFirst body ".spl-bulk-bar button:contains(Move)")))))
