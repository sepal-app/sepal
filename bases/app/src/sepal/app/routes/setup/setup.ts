// Minimal setup wizard dependencies
import Alpine from "alpinejs"
import morph from "@alpinejs/morph"
import htmx from "htmx.org"
import "htmx-ext-alpine-morph"

import TimezoneField from "~/js/timezone-field"
import SetupProgress from "~/routes/setup/taxonomy"

window.htmx = htmx
window.Alpine = Alpine

Alpine.plugin(morph)

document.addEventListener("alpine:init", () => {
    Alpine.directive("timezone-field", TimezoneField)
    Alpine.directive("setup-progress", SetupProgress)
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
