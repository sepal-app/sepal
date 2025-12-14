import Alpine from "alpinejs"
import collapse from "@alpinejs/collapse"
import focus from "@alpinejs/focus"
import ui from "@alpinejs/ui"
import morph from "@alpinejs/morph"
import htmx from "htmx.org"

window.htmx = htmx

import FormState from "~/js/form-state"

window.Alpine = Alpine

Alpine.plugin(collapse)
Alpine.plugin(focus)
Alpine.plugin(morph)
Alpine.plugin(ui)

document.addEventListener("alpine:init", () => {
    // setup global directives
    Alpine.directive("form-state", FormState)
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
