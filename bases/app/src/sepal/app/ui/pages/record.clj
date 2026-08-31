(ns sepal.app.ui.pages.record
  "The shell every record page composes through.

  A record page is the same shape whichever section you are looking at: the
  record identifies itself, its sections are listed, its body sits in a padded
  region, and its actions sit at the foot. Each section supplies only its body.

  This exists because the alternative had already gone wrong: the General tab
  carried padding because its body happened to emit a class that had some,
  while the Media tab emitted different markup and had none. When each page
  decides its own frame, the frames diverge — and they diverge silently, on
  whichever screen nobody happened to look at."
  (:require [sepal.app.ui.page :as ui.page]))

(defn page
  "Compose a record page.

  :code   the record's identifier, set in mono brand green.
  :name   the record's name, rendered by the caller so a taxon name can go
          through the name renderer.
  :tabs   the section nav, from `ui.tabs/tabs`.
  :body   this section's content, and nothing else — no frame, no padding.
  :footer optional action bar, pinned to the foot of the column."
  [& {:keys [code name tabs body footer]}]
  [:div {:class "spl-record-page"}
   (ui.page/record-header :code code :name name)
   tabs
   [:div {:class "spl-record-body"} body]
   footer])
