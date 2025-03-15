import Alpine from "alpinejs"
import collapse from "@alpinejs/collapse"
import focus from "@alpinejs/focus"
import ui from "@alpinejs/ui"
import morph from "@alpinejs/morph"
import "htmx.org"

window.Alpine = Alpine

Alpine.plugin(collapse)
Alpine.plugin(focus)
Alpine.plugin(morph)
Alpine.plugin(ui)

Alpine.start()
