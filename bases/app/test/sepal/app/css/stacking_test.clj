(ns sepal.app.css.stacking-test
  "The rail's tooltips paint outside the rail, over the page. Whether they are
   visible there is decided by stacking order, which no markup test can see —
   the tooltip renders correctly and is simply painted behind something. So
   these tests read the stylesheet, the way tokens-test does.

   The bug they pin: `.spl-rail` is `position: sticky`, which always forms a
   stacking context. With no z-index that context sits in the `auto` group and
   paints below `.spl-table thead th` at 2, which is opaque. Every rail tooltip
   overlapping a list's sticky header disappeared behind it, and the tooltip's
   own z-index could not help — it orders the tip inside the rail, not against
   the page."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]))

(def ^:private stylesheet "sepal/app/css/components.css")

(defn- css []
  (let [r (io/resource stylesheet)]
    (assert (some? r) (str "missing " stylesheet " on the classpath"))
    (slurp r)))

(defn- z-index
  "The z-index declared in `selector`'s top-level rule. Anchored at the start of
   a line, so an indented copy inside a media query is not what we read.

   Throws rather than returning nil when the declaration is missing: a nil here
   means the rule relies on `auto`, which is the bug, and an explicit message
   beats a NullPointerException three frames later."
  [selector]
  (let [pattern (re-pattern (str "(?m)^\\Q" selector "\\E \\{([^}]*)\\}"))
        block (second (re-find pattern (css)))
        _ (assert (some? block) (str "no top-level rule for " selector))
        z (second (re-find #"z-index:\s*(-?\d+)" block))]
    (assert (some? z) (str selector " declares no z-index, so it relies on `auto`"))
    (parse-long z)))

(deftest test-rail-outranks-the-sticky-table-header
  (testing "regression: the rail had no z-index, so its stacking context sat at
            `auto` and a list's sticky header — opaque, at 2 — painted over
            every tooltip the rail put on the page"
    (let [rail (z-index ".spl-rail")
          header (z-index ".spl-table thead th")]
      (is (> rail header)
          (str "rail at " rail " loses to the table header at " header)))))

(deftest test-rail-stays-under-the-flash-banner
  (testing "the other half of the invariant. Raising the rail far enough to
            clear the header must not float it over the banner, which is fixed
            across the bottom of the viewport and has to stay readable."
    (is (< (z-index ".spl-rail") (z-index ".spl-banner-stack")))))
