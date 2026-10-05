(ns sepal.app.list-view-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.middleware.anti-forgery :as anti-forgery]
            [sepal.app.list-view :as list-view]
            [sepal.app.ui.pages.list :as pages.list]
            [zodiac.core :as z]))

(def columns
  [{:key :code :type :identifier :priority 1 :sort [:a.code]}
   {:key :provenance :type :text :priority 3 :sort [:a.provenance_type]}
   {:key :supplier :type :text :priority 3 :hidden? true :sort [:sc.name]}])

(defn- request [& {:keys [params headers overrides]}]
  {:uri "/accession/"
   :query-params (or params {})
   :headers (or headers {})
   :viewer {:user/list-columns (when overrides {:accession overrides})}})

(def toolbar {"hx-request" "true" "hx-trigger" "list-toolbar"})

(deftest test-columns-come-from-the-viewer
  (is (= [:code :provenance] (map :key (:columns (list-view/resolve (request) :accession columns)))))
  (let [view (list-view/resolve (request :overrides {:supplier true}) :accession columns)]
    (is (= [:code :provenance :supplier] (map :key (:columns view))))
    (is (:custom? view))))

(deftest test-sort-from-params
  (is (= :provenance (get-in (list-view/resolve (request :params {"sort" "provenance" "dir" "asc"})
                                                :accession columns)
                             [:sort :column :key])))
  (is (nil? (:sort (list-view/resolve (request :params {"sort" "code" "options" ""}) :accession columns)))
      "a combobox asking for options gets rank order"))

(deftest test-carry-scope
  (let [current "http://localhost/accession/?q=quer&sort=provenance&dir=asc"]
    (testing "the toolbar refining a search keeps the sort"
      (let [view (list-view/resolve (request :params {"q" "querc"}
                                             :headers (assoc toolbar "hx-current-url" current))
                                    :accession columns)]
        (is (= :provenance (get-in view [:sort :column :key])))
        (is (:carried? view))))
    (testing "a request that isn't the toolbar doesn't carry"
      (is (nil? (:sort (list-view/resolve (request :params {"q" "querc"}
                                                   :headers {"hx-request" "true" "hx-current-url" current})
                                          :accession columns)))))
    (testing "a page on another path doesn't carry"
      (is (nil? (:sort (list-view/resolve (request :params {"q" "querc"}
                                                   :headers (assoc toolbar "hx-current-url"
                                                                   "http://localhost/taxon/?sort=provenance"))
                                          :accession columns)))))))

(deftest test-href
  (let [view (list-view/resolve (request :params {"sort" "provenance" "dir" "asc"}) :accession columns)]
    (is (= "/accession/?sort=provenance&dir=asc&page=2&q=quer"
           (list-view/href view "/accession/" "quer" :page 2)))
    (is (= "/accession/" (list-view/href (assoc view :sort nil) "/accession/" "")))))

(deftest test-table-opts
  (with-redefs [z/url-for (constantly "/lists/accession/columns")]
    (binding [anti-forgery/*anti-forgery-token* "token"]
      (let [view (list-view/resolve (request) :accession columns)
            opts (list-view/table-opts view "/accession/" "quer")]
        (is (:shed? opts) "no choice made: shed as usual")
        (is (not (:min-width? opts)))
        (is (= "/accession/?sort=provenance&dir=asc&q=quer"
               (:href ((:sort-link opts) (second columns)))))
        (is (= (str "#" pages.list/toolbar-id ":replace") (:hx-sync ((:sort-link opts) (second columns))))
            "a header click and a toolbar request share one sync slot")
        (is (some? (:picker opts)))))))

(deftest test-respond-pushes-a-carried-sort
  (let [view {:carried? true :sort {:column (second columns) :dir :asc}}
        response (list-view/respond view [:p "x"] "/accession/" "quer")]
    (is (= "/accession/?sort=provenance&dir=asc&q=quer" (get-in response [:headers "HX-Push-Url"]))))
  (is (= [:p "x"] (list-view/respond {} [:p "x"] "/accession/" ""))
      "without a carried sort the page is returned for the middleware to render"))
