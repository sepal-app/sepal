(ns sepal.app.globals)

(def ^:dynamic *viewer* nil)

;; The request URI, so deeply-nested UI can tell which section it is in without
;; every caller of ui.page/page threading a route through. Bound alongside
;; *viewer* in sepal.app.middleware/require-viewer.
(def ^:dynamic *uri* nil)

;; Whether the section rail is expanded, from a cookie the toggle writes.
;; Read server-side rather than from localStorage so the rail is already in the
;; right state on first paint — restoring it in script would render collapsed
;; and then jump.
(def ^:dynamic *rail-open?* false)
