import Alpine from "alpinejs"
import collapse from "@alpinejs/collapse"
import focus from "@alpinejs/focus"
import ui from "@alpinejs/ui"
import morph from "@alpinejs/morph"
// htmx not needed on auth pages - forms use regular POST

window.Alpine = Alpine

Alpine.plugin(collapse)
Alpine.plugin(focus)
Alpine.plugin(morph)
Alpine.plugin(ui)

Alpine.start()
