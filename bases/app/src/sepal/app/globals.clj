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

;; What the garden calls itself, for the browser tab. Bound in
;; sepal.app.middleware/wrap-org-settings rather than read from z/*request*:
;; that var is bound before the route middleware runs, so the context this
;; middleware adds to is not the one a renderer would see. nil when the garden
;; has not named itself, so a caller leaves the part out.
(def ^:dynamic *organization-name* nil)
