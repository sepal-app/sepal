(ns sepal.app.routes.setup.taxonomy
  (:require [clojure.data.json :as json]
            [sepal.app.html :as html]
            [sepal.app.http-response :as http]
            [sepal.app.routes.setup.layout :as layout]
            [sepal.app.routes.setup.routes :as setup.routes]
            [sepal.app.routes.setup.shared :as setup.shared]
            [sepal.app.ui.form :as form]
            [sepal.database.interface :as db.i]
            [zodiac.core :as z]))

(defn render-taxa-exist
  "Render the view when taxa already exist in the database."
  [& {:keys [taxa-count flash-messages]}]
  (layout/layout
    :current-step 5
    :flash-messages flash-messages
    :content
    (layout/step-card
      :title "Taxonomy Data"
      :back-url (z/url-for setup.routes/regional)
      :content
      [:div {:class "space-y-4"}
       [:div {:class "spl-alert spl-alert--info"}
        [:div
         [:p {:class "font-medium"} "Taxonomy data already exists"]
         [:p (format "Your database already contains %,d taxa. The WFO Plant List import is only available for empty databases to avoid conflicts with existing taxonomic data."
                     taxa-count)]]]
       [:p {:class "text-text-muted"}
        "You can continue using your existing taxa, or contact an administrator to reset the database if you want to start fresh with WFO data."]]
      :next-button
      [:a {:href (z/url-for setup.routes/review)
           :class "spl-btn spl-btn--primary"}
       "Continue →"])))

(defn render-import-available
  "Render the view when WFO import is available."
  [& {:keys [flash-messages]}]
  (layout/layout
    :current-step 5
    :flash-messages flash-messages
    :content
    (layout/step-card
      :title "Taxonomy Data"
      :back-url (z/url-for setup.routes/regional)
      :content
      [:div {:class "space-y-4"}
       [:p {:class "text-text-muted"}
        "Sepal can import the World Flora Online (WFO) Plant List, a comprehensive database of plant names and their taxonomic status."]

       [:div {:class "bg-surface-alt p-4 rounded-lg"}
        [:h4 {:class "font-medium mb-2"} "What you'll get:"]
        [:ul {:class "list-disc list-inside text-sm text-text-muted space-y-1"}
         [:li "Over 450,000 plant taxa"]
         [:li "Scientific names with authors"]
         [:li "Taxonomic hierarchy (family, genus, species)"]]]

       [:div {:class "flex gap-3 mt-4"}
        [:form {:method "post"
                :action (z/url-for setup.routes/taxonomy)
                :x-data "{ submitting: false }"
                :x-on:submit "submitting = true"}
         (form/anti-forgery-field)
         [:input {:type "hidden" :name "action" :value "import"}]
         [:button {:type "submit"
                   :class "spl-btn spl-btn--primary"
                   :x-bind:disabled "submitting"
                   :x-bind:class "submitting && 'spl-loading'"}
          "Import WFO Plant List"]]
        [:a {:href (z/url-for setup.routes/review)
             :class "spl-btn spl-btn--ghost"
             :x-show "!submitting"}
         "Skip for now"]]]
      :next-button nil)))

(def ^:private phase-labels
  "What each phase is called on screen. Downloads name their file because the
  two are minutes apart in size and a bar with no label looks stuck."
  {"idle" "Waiting to start…"
   "fetching-manifest" "Looking up the latest WFO Plant List…"
   "downloading-taxa" "Downloading the plant list…"
   "importing-taxa" "Importing taxa. This takes a minute and shows no percentage."
   "downloading-synonyms" "Downloading the synonym reference…"
   "done" "Done."
   "failed" "The import failed."})

(defn render-import-running
  "Render the progress view. The initial frame is inlined into x-data so the
  page is correct before the first SSE message arrives, and a reload during a
  download rejoins the stream instead of restarting the job."
  [& {:keys [state flash-messages]}]
  ;; No script tag here: the x-setup-progress directive is registered by
  ;; setup.ts, which the wizard layout loads on every step. This page is reached
  ;; by an hx-boost swap, long after alpine:init has fired.
  (layout/layout
    :current-step 5
    :flash-messages flash-messages
    :content
    (layout/step-card
      :title "Taxonomy Data"
      :back-url nil
      :content
      [:div {:class "space-y-4"
             :x-data (json/write-str (assoc (setup.shared/job-frame state)
                                            "labels" phase-labels))
             :x-setup-progress (z/url-for setup.routes/taxonomy-progress)
             :data-done-url (z/url-for setup.routes/review)}
       [:p {:class "text-text-muted"
            :x-text "labels[phase]"}
        (get phase-labels (name (:phase state)))]

       ;; No spl- progress class exists and 023 owns the visual language, so the
       ;; bar is built from utilities rather than by adding a rule here.
       [:div {:class "h-2 w-full rounded-full bg-border overflow-hidden"
              :role "progressbar"
              :x-bind:aria-valuenow "percent"}
        [:div {:class "h-full bg-brand transition-all"
               :x-bind:style "percent === null ? 'width: 100%' : `width: ${percent}%`"}]]

       [:p {:class "text-sm text-text-muted"
            :x-show "percent !== null"
            :x-cloak true}
        [:span {:x-text "percent"}] "%"
        [:span {:x-show "approximate"} " (approximate)"]]

       [:div {:class "spl-alert spl-alert--danger"
              :x-show "phase === 'failed'"
              :x-cloak true}
        [:p {:x-text "error"}]]

       [:div {:class "spl-alert spl-alert--warning"
              :x-show "warning"
              :x-cloak true}
        [:p {:x-text "warning"}]]

       [:div {:class "flex gap-3 mt-4"
              :x-show "phase === 'failed'"
              :x-cloak true}
        [:form {:method "post"
                :action (z/url-for setup.routes/taxonomy)}
         (form/anti-forgery-field)
         [:button {:type "submit" :class "spl-btn spl-btn--primary"} "Try again"]]
        [:a {:href (z/url-for setup.routes/review)
             :class "spl-btn spl-btn--ghost"}
         "Skip for now"]]]
      :next-button nil)))

(defn handler [{:keys [::z/context flash request-method]}]
  (let [{:keys [db setup-job synonym-ref-path]} context
        can-import? (setup.shared/can-import-wfo? db)
        taxa-count (db.i/count db {:select [:id] :from [:taxon]})]

    (case request-method
      :post
      ;; can-import-wfo? is the guard, not decoration: setup routes carry no
      ;; auth middleware, so this is what stops an unauthenticated caller
      ;; kicking off a 127 MB download against an already-configured install.
      (if-not can-import?
        (http/see-other setup.routes/review)
        (do
          ;; :ref-dest comes from the process, which is where the one
          ;; per-machine reference file is decided. env-opts always resolves it;
          ;; a caller that passes none gets a recorded warning rather than a
          ;; download, because the reference is an enhancement.
          (setup.shared/start-import! db setup-job {:ref-dest synonym-ref-path})
          (setup.shared/set-current-step! db 5)
          (html/render-page
            (render-import-running :state @setup-job
                                   :flash-messages (:messages flash)))))

      ;; GET
      (do
        (setup.shared/set-current-step! db 5)
        (html/render-page
          (cond
            (not= :idle (:phase @setup-job))
            (render-import-running :state @setup-job
                                   :flash-messages (:messages flash))

            can-import?
            (render-import-available :flash-messages (:messages flash))

            :else
            (render-taxa-exist :taxa-count taxa-count
                               :flash-messages (:messages flash))))))))

(defn progress-handler
  "Stream the import job's progress as Server-Sent Events.

  Progress travels server→client only, so SSE rather than WebSockets: no
  protocol upgrade, it rides the existing middleware on a plain GET, and
  EventSource reconnects on its own."
  [{:keys [::z/context]}]
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache"
             ;; nginx buffers proxied responses by default, which would undo
             ;; the per-frame flush below it.
             "X-Accel-Buffering" "no"}
   :body (setup.shared/sse-body (:setup-job context))})
