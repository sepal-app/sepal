import Alpine from "alpinejs"
import collapse from "@alpinejs/collapse"
import focus from "@alpinejs/focus"
import ui from "@alpinejs/ui"
import morph from "@alpinejs/morph"
import htmx from "htmx.org"
import "htmx-ext-alpine-morph"

window.htmx = htmx

import FormState from "~/js/form-state"
import { queryBuilder, accessionsOnlyFilter } from "~/js/query-builder"
import { defineCombobox } from "~/js/record-combobox-element"

window.Alpine = Alpine

Alpine.plugin(collapse)
Alpine.plugin(focus)
Alpine.plugin(morph)
Alpine.plugin(ui)

// Custom elements register themselves; htmx swaps re-run connectedCallback,
// which is the seam a picker has to survive.
defineCombobox()

document.addEventListener("alpine:init", () => {
    // setup global directives
    Alpine.directive("form-state", FormState)

    // setup global data components
    Alpine.data("queryBuilder", queryBuilder)
    Alpine.data("accessionsOnlyFilter", accessionsOnlyFilter)
})

// A rejected save swaps each field's error list in, but not the control — it
// holds what you typed. So the control never learned it was invalid, and the
// styling for that only ever showed on a full page render. The list's id is
// `<field>-errors` and the control's is `<field>`.
function markFieldFromErrors(swapped: Element | null | undefined) {
    if (!(swapped instanceof HTMLElement)) return
    const id = swapped.id
    if (!id.endsWith("-errors")) return
    // Read the list that is now in the document, not the node the event
    // carries: that one has already been replaced, so its content is the
    // empty list from before the swap.
    const list = document.getElementById(id)
    const field = document.getElementById(id.slice(0, -"-errors".length))
    if (!list || !field) return
    if (list.textContent && list.textContent.trim()) {
        field.setAttribute("aria-invalid", "true")
    } else {
        field.removeAttribute("aria-invalid")
    }
}

document.addEventListener("htmx:oobAfterSwap", (evt: Event) => {
    markFieldFromErrors((evt as CustomEvent).detail?.target)
})

// Allow 422 responses to be processed by HTMX for OOB error swaps
document.addEventListener("htmx:beforeSwap", (evt: Event) => {
    const event = evt as CustomEvent
    if (event.detail.xhr.status === 422) {
        event.detail.shouldSwap = true
        event.detail.isError = false
    }
})

Alpine.start()
