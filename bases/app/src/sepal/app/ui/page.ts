import Alpine from "alpinejs"
import collapse from "@alpinejs/collapse"
import focus from "@alpinejs/focus"
import ui from "@alpinejs/ui"
import morph from "@alpinejs/morph"
import "htmx.org"

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

Alpine.start()
