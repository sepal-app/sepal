(ns sepal.app.ui.dropdown)

(def x-data "
{open: false,
toggle() {
  if (this.open) {
    return this.close()
  }

  this.$refs.button.focus()
  this.open = true
},
close(focusAfter) {
  if (! this.open) return
  this.open = false
  focusAfter && focusAfter.focus()
  }
}")

(defn item [href text]
  [:a {:href href
       :class "flex items-center gap-2 w-full first-of-type:rounded-t-md last-of-type:rounded-b-md px-4 py-2.5 text-left text-sm hover:bg-gray-50 disabled:text-gray-500"}
   text])

(defn dropdown [label items]
  [:div {:class "flex justify-center"}
   [:div {:x-data x-data
          :x-on:keydown.escape.prevent.stop "close($refs.button)",
          :x-on:focusin.window "! $refs.panel.contains($event.target) && close()",
          :x-id "['dropdown-button']",
          :class "relative"}
    ;; "Button"
    [:button {:x-ref "button",
              :x-on:click "toggle()",
              ::aria-expanded "open",
              ::aria-controls "$id('dropdown-button')",
              :type "button",
              :class "flex items-center gap-2 bg-white px-5 py-2.5 rounded-md shadow"}
     label
     ;; "Heroicon: chevron-down"
     [:svg {:xmlns "http://www.w3.org/2000/svg",
            :class "h-5 w-5 text-gray-400",
            :viewBox "0 0 20 20",
            :fill "currentColor"}
      [:path {:fill-rule "evenodd",
              :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z",
              :clip-rule "evenodd"}]]]
    ;; "Panel")
    [:div {:x-ref "panel",
           :x-show "open",
           :x-transition.origin.top.left "",
           :x-on:click.outside "close($refs.button)",
           ::id "$id('dropdown-button')",
           :style "display: none;",
           :class "absolute left-0 mt-2 w-40 rounded-md bg-white shadow-md z-10"}
     [:<> items]]]])
