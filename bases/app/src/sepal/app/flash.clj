(ns sepal.app.flash
  (:require [sepal.app.flash.category :as category]
            [sepal.app.html :as html]
            [sepal.app.ui.icons.lucide :as lucide]))

(defn add-message
  ([response text]
   (add-message response text :info))
  ([response text category]
   (update-in response
              [:flash :messages]
              (fnil conj [])
              {:text text :category category})))

(defn error [response text]
  (add-message response text category/error))

(defn success [response text]
  (add-message response text category/success))

#_(defn error-seq
    "Create a validation-seq from humanized malli validation error.

  A validation-seq is a sequence of list of maps with a message key and optional
  field key and :error metadata key is true.
  "
    [error]
    (cond
      ;; Convert map of field errors into array of maps with keys field and message
      (map? error)
      (reduce-kv (fn [acc k v] (conj acc {:field k :messages v})) [] error)

      (string? error)
      {:messages [error]}

      (nil? error)
      {:messages nil}

      ;; Convert sequence of errors into array of maps with a message key
      (seqable? error)
      (map error-seq error)))

(defn set-field-errors
  [response field-errors]
  (assoc-in response
            [:flash :field-errors]
            field-errors))

(defn field-error [request field]
  (get-in request [:flash :field-errors field]))

(def ^:private variants
  {category/error "spl-banner--danger"
   category/warning "spl-banner--warning"
   category/success "spl-banner--ok"
   category/info "spl-banner--info"})

(defn banner-message [message]
  (let [{:keys [text category]} message
        ;; Errors don't auto-dismiss; success/info dismiss after 5s
        auto-dismiss? (not= category category/error)
        timeout-ms 5000]
    ;; `banner` and `banner-text` are kept alongside the design-system classes:
    ;; the HTTP tests select on them to read a flash message.
    [:div {:class (html/attr "spl-banner" "banner"
                             (get variants category "spl-banner--info"))
           ;; An error a reader has to act on, announced as soon as it lands;
           ;; a success they can catch up with, announced politely.
           :role (if (= category category/error) "alert" "status")
           :x-data "{show: true}"
           :x-show "show"
           :x-init (when auto-dismiss?
                     (format "setTimeout(() => show = false, %d)" timeout-ms))
           :x-transition:leave "transition ease-in duration-300"
           :x-transition:leave-start "opacity-100"
           :x-transition:leave-end "opacity-0"}
     [:p {:class "spl-banner-text banner-text"} text]
     [:button {:type "button"
               :class "spl-banner-dismiss"
               :aria-label "Dismiss"
               :x-on:click "show = false"}
      ;; `icons/outline-x` renders an empty 5px svg — it has no path and its
      ;; size is unitless. The banner needs a mark you can actually see.
      (lucide/x :class "w-4 h-4")]]))

(defn banner [messages]
  [:div {:class "spl-banner-stack"}
   (for [message messages]
     (banner-message message))])

(defn banner-oob
  "Renders flash messages as OOB swap element for #flash-container.
   Uses beforeend to append new messages rather than replacing existing ones.
   Used by middleware for HTMX responses."
  [messages]
  [:div {:id "flash-container"
         :hx-swap-oob "beforeend"}
   (when (seq messages)
     (banner messages))])
