(ns sepal.app.globals)

(def ^:dynamic *viewer* nil)

;; The request URI, so deeply-nested UI can tell which section it is in without
;; every caller of ui.page/page threading a route through. Bound alongside
;; *viewer* in sepal.app.middleware/require-viewer.
(def ^:dynamic *uri* nil)
