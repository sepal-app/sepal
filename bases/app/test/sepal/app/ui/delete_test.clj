(ns sepal.app.ui.delete-test
  (:require [clojure.test :refer [deftest is]]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.ui.delete :as ui.delete])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup]
  (binding [*anti-forgery-token* "test-token"]
    (Jsoup/parseBodyFragment (chassis/html hiccup))))

(deftest test-a-deletable-record-offers-the-delete
  (let [body (parse (ui.delete/dialog :action "/accession/12/delete/"
                                      :label "accession 2004.0231"
                                      :blockers []))
        form (.selectFirst body "form[method=post]")]
    (is (some? form))
    (is (= "/accession/12/delete/" (.attr form "action")))
    (is (some? (.selectFirst body "input[name=__anti-forgery-token]")))
    (is (re-find #"2004\.0231" (.text body))
        "The dialog names the record, so a mis-click is visible before it lands")
    (is (re-find #"(?i)cannot be undone" (.text body))
        "A hard delete says so")))

(deftest test-a-blocked-record-offers-no-delete
  (let [body (parse (ui.delete/dialog
                      :action "/accession/12/delete/"
                      :label "accession 2004.0231"
                      :blockers [{:reason :material :count 12}]))]
    (is (nil? (.selectFirst body "form[method=post]"))
        "No form means no way to submit a delete the server would refuse")
    (is (re-find #"12 material" (.text body))
        "and the reason is named with its count")))

(deftest test-every-blocker-is-listed
  (let [body (parse (ui.delete/dialog
                      :action "/location/3/delete/"
                      :label "location Block 24"
                      :blockers [{:reason :material :count 3}
                                 {:reason :material-change :count 18}]))]
    (is (= 2 (.size (.select body "li")))
        "a curator fixing one reason needs to see the other")
    (is (re-find #"3 material" (.text body)))
    (is (re-find #"18 move" (.text body)))))

(deftest test-the-button-fetches-the-dialog
  (let [body (parse (ui.delete/button :delete-url "/accession/12/delete/"))
        button (.selectFirst body "button")]
    (is (= "/accession/12/delete/" (.attr button "hx-get")))
    (is (= "#delete-modal-container" (.attr button "hx-target")))
    (is (seq (.attr button "hx-on::after-swap"))
        "the dialog is opened after it arrives")
    (is (some? (.selectFirst body "#delete-modal-container")))))

(deftest test-the-markup-carries-no-fragment-element
  ;; Chassis has no fragment element: [:<> ...] renders a literal <<>> tag.
  ;; ui/resource_panel.clj warns about this, and the button returns two
  ;; siblings, which is exactly where it is tempting to reach for one.
  (let [html (binding [*anti-forgery-token* "test-token"]
               (chassis/html (ui.delete/button :delete-url "/x/1/delete/")))]
    (is (not (re-find #"<<>>|</<>>" html)))))
