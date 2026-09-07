(ns sepal.app.globals)

(def ^:dynamic *viewer* nil)

;; The request URI, so deeply-nested UI can tell which section it is in without
;; every caller of ui.page/page threading a route through. Bound alongside
;; *viewer* in sepal.app.middleware/require-viewer.
(def ^:dynamic *uri* nil)

;; Whether this database has the tag tables, from sepal.tag.interface/available?.
;; Bound alongside *viewer* in sepal.app.middleware/require-viewer.
;;
;; A global rather than an argument for the same reason *uri* is one: the two
;; places that need it are the section rail — rendered by ui.page/page on every
;; page, which has no route context to thread — and the Tags tab in three
;; detail/shared namespaces, reached through every section of those records.
;; Defaults to false so anything rendering outside require-viewer omits the
;; entry rather than offering one that 500s.
(def ^:dynamic *tags-available?* false)

;; Whether the section rail is expanded, from a cookie the toggle writes.
;; Read server-side rather than from localStorage so the rail is already in the
;; right state on first paint — restoring it in script would render collapsed
;; and then jump.
(def ^:dynamic *rail-open?* false)
