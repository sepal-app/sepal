(ns sepal.app.ui.tooltip
  "The label an icon-only control needs when nothing beside it says what it does.

  The tip is decorative — `aria-hidden`, no id, no `aria-describedby`. Its text
  repeats the control's `aria-label`, and a description identical to the
  accessible name only makes a screen reader say the word twice. Every control
  wrapped here must already carry an `aria-label`; the tip does not supply one.

  Shown on hover and on `:focus-visible`, in CSS. No JavaScript, so it is right
  on first paint and needs no positioning pass. It is absolutely positioned
  inside the control, so an ancestor that clips clips it too — see the
  `.spl-tip` comment in components.css.")

(def ^:private sides
  "Which edge of the control the tip sits on."
  #{"right" "left" "top" "bottom"})

(defn tip
  "The tip element on its own, for a control that builds its own markup."
  [text & {:keys [side] :or {side "bottom"}}]
  {:pre [(contains? sides side)]}
  [:span {:class ["spl-tip" (str "spl-tip--" side)]
          :aria-hidden "true"}
   text])

(defn- add-class
  "Append `extra` to a :class that may be missing, a string, or a vector —
  all three forms are in use across the call sites."
  [class extra]
  (cond
    (nil? class) [extra]
    (sequential? class) (conj (vec class) extra)
    :else [class extra]))

(defn wrap
  "Give `control` a tooltip reading `text`.

  `control` is a hiccup vector, with or without an attrs map. It gains the host
  class and the tip as its last child — no wrapper element, so nothing about
  the surrounding layout changes."
  [control text & {:keys [side] :or {side "bottom"}}]
  (let [[tag & more] control
        attrs (when (map? (first more)) (first more))
        children (if attrs (next more) more)]
    (into [tag (update (or attrs {}) :class add-class "spl-tip-host")]
          (concat children [(tip text :side side)]))))
