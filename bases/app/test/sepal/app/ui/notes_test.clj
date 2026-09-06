(ns sepal.app.ui.notes-test
  (:require [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [sepal.app.ui.notes :as ui.notes])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup]
  (binding [*anti-forgery-token* "test-token"]
    (Jsoup/parseBodyFragment (chassis/html hiccup))))

(def ^:private notes
  [{:note/id 2
    :note/body "second note"
    :note/created-at "2011-10-11 00:00:00"
    :note/author-email "curator@example.com"}
   {:note/id 1
    :note/body "imported, no author"
    :note/created-at "2010-12-08 00:00:00"
    :note/author-email nil}])

(defn- note-url-fn [note-id]
  (str "/accession/12/notes/" note-id "/"))

(deftest test-note-list-renders-every-note
  (let [body (parse (ui.notes/note-list :notes notes :note-url-fn note-url-fn))]
    (is (some? (.selectFirst body "#notes-list")))
    (is (= 2 (.size (.select body "[data-note-id]"))))
    (testing "newest first, in the order given"
      (is (= ["2" "1"]
             (mapv #(.attr % "data-note-id") (.select body "[data-note-id]")))))
    (testing "the author is shown when there is one"
      (is (= "curator@example.com"
             (.text (.selectFirst body "[data-note-id=2] [data-note-author]")))))
    (testing "and the element is absent when there is not"
      (is (nil? (.selectFirst body "[data-note-id=1] [data-note-author]"))))))

(deftest test-note-body-is-text-not-markup
  (let [body (parse (ui.notes/note-list
                      :notes [{:note/id 1
                               :note/body "<script>alert(1)</script>"
                               :note/created-at "2011-10-11 00:00:00"
                               :note/author-email nil}]
                      :note-url-fn note-url-fn))]
    (is (nil? (.selectFirst body "script"))
        "A note body is plain text and must never render as markup")))

(deftest test-each-note-carries-its-own-edit-form
  (let [body (parse (ui.notes/note-list :notes notes :note-url-fn note-url-fn))
        form (.selectFirst body "[data-note-id=2] form")]
    (is (some? form))
    (is (= "/accession/12/notes/2/" (.attr form "hx-post")))
    (is (= "#notes-list" (.attr form "hx-target")))
    (is (= "outerHTML" (.attr form "hx-swap")))))

(deftest test-delete-targets-the-list
  (let [body (parse (ui.notes/note-list :notes notes :note-url-fn note-url-fn))
        button (.selectFirst body "[data-note-id=2] [hx-delete]")]
    (is (= "/accession/12/notes/2/" (.attr button "hx-delete")))
    (is (= "#notes-list" (.attr button "hx-target")))
    (is (not (empty? (.attr button "hx-confirm")))
        "Deleting a note asks first")
    (is (re-find #"test-token" (.attr button "hx-headers"))
        "A bodyless hx-delete carries no CSRF token unless hx-headers supplies one")))

(deftest test-the-new-note-form-clears-itself-after-a-successful-post
  (let [body (parse (ui.notes/notes-body :notes notes
                                         :create-url "/accession/12/notes/"
                                         :note-url-fn note-url-fn))
        form (.selectFirst body "form#note-form")
        handler (.attr form "hx-on::after-request")]
    (is (re-find #"this\.reset\(\)" handler)
        "The swap replaces the list, not the form, so the form must clear itself")
    (is (re-find #"dirty = false" handler)
        "x-form-state only sets dirty true; without this the submit button
         stays enabled over an empty textarea")
    (is (re-find #"event\.detail\.successful" handler)
        "A rejected post keeps the text where the curator can fix it")))

(deftest test-empty-list-says-so
  (let [body (parse (ui.notes/note-list :notes [] :note-url-fn note-url-fn))]
    (is (zero? (.size (.select body "[data-note-id]"))))
    (is (some? (.selectFirst body "[data-notes-empty]")))))

(deftest test-panel-section-is-read-only
  (let [body (parse (ui.notes/panel-section :notes notes
                                            :note-count 7
                                            :more-url "/accession/12/notes/"))]
    (is (zero? (.size (.select body "form")))
        "The panel is the read-only view; nothing in it writes")
    (is (zero? (.size (.select body "button"))))
    (is (some? (.selectFirst body "a[href=/accession/12/notes/]"))
        "and it links to the tab for the rest")))
