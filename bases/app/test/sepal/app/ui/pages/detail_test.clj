(ns sepal.app.ui.pages.detail-test
  "The detail layout takes :content, :panel-content and :footer.

   Location and contact detail both passed `:panel` instead of `:panel-content`
   — a key the layout never reads — so both pages rendered with no resource
   panel at all, silently, for as long as the typo existed. Nothing failed,
   because a Clojure keyword argument that nobody destructures simply
   evaporates."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dev.onionpancakes.chassis.core :as chassis]
            [sepal.app.ui.pages.detail :as pages.detail])
  (:import [org.jsoup Jsoup]))

(defn- parse [hiccup] (Jsoup/parseBodyFragment (chassis/html hiccup)))

(deftest test-renders-content-panel-and-footer
  (let [body (parse (pages.detail/page-content-with-panel
                      :content [:p {:id "c"} "content"]
                      :panel-content [:p {:id "p"} "panel"]
                      :footer [:div {:id "f"} "footer"]))]
    (is (some? (.selectFirst body "#c")))
    (is (some? (.selectFirst body "#p")))
    (is (some? (.selectFirst body "#f")))))

(deftest test-footer-sits-in-the-content-column
  (testing "rendered as a sibling of the panes it lands under the panel and
            falls off the bottom of the screen"
    (let [body (parse (pages.detail/page-content-with-panel
                        :content [:p "c"]
                        :panel-content [:p "p"]
                        :footer [:div {:id "f"} "footer"]))]
      (is (some? (.selectFirst body ".spl-detail-main #f"))
          "the footer belongs to the form column, not the pane row"))))

(deftest test-panel-goes-in-the-panel-column
  (let [body (parse (pages.detail/page-content-with-panel
                      :content [:p "c"]
                      :panel-content [:p {:id "p"} "panel"]))]
    (is (some? (.selectFirst body ".spl-detail-panel #p")))))

(def ^:private detail-pages
  ["routes/accession/detail/general.clj"
   "routes/accession/detail/collection.clj"
   "routes/accession/detail/media.clj"
   "routes/taxon/detail/name.clj"
   "routes/taxon/detail/media.clj"
   "routes/material/detail/general.clj"
   "routes/material/detail/media.clj"
   "routes/location/detail.clj"
   "routes/contact/detail.clj"])

(defn- source [rel]
  (slurp (io/file (str "bases/app/src/sepal/app/" rel))))

(deftest test-no-page-passes-an-unread-panel-key
  (testing "`:panel` is not a key this layout reads; passing it drops the panel"
    (doseq [rel detail-pages
            :let [src (source rel)]]
      (is (not (re-find #":panel\s+\(" src))
          (format "%s passes :panel — the layout reads :panel-content" rel)))))

(defn- balanced-form
  "The text of the first form starting at `marker`, read to its matching paren.
   Regex cannot tell an argument of this call from an argument of a nested one;
   counting parens can."
  [src marker]
  (when-let [start (str/index-of src marker)]
    (loop [i start depth 0]
      (if (>= i (count src))
        (subs src start)
        (let [c (nth src i)
              depth (case c \( (inc depth) \) (dec depth) depth)]
          (if (and (= c \)) (zero? depth))
            (subs src start (inc i))
            (recur (inc i) depth)))))))

(deftest test-detail-footers-go-into-the-content-column
  (testing "a detail page's action bar is passed to page-content, which puts it
            in the form column. Passed to page/page it renders after the whole
            two-pane block, lands under the panel and falls off the screen.

            Only pages that build a footer are checked — a media section has no
            form to submit."
    (doseq [rel detail-pages
            :let [src (source rel)]
            :when (str/includes? src "ui.form/footer")]
      (let [call (balanced-form src "(page-content ")]
        (is (some? call) (format "%s has no page-content call" rel))
        (is (str/includes? (or call "") ":footer")
            (format "%s builds a footer but does not pass it to page-content"
                    rel))))))
