import Alpine from "alpinejs"
import collapse from "@alpinejs/collapse"
import focus from "@alpinejs/focus"
import ui from "@alpinejs/ui"
import morph from "@alpinejs/morph"
import validate from "@colinaut/alpinejs-plugin-simple-validate"
import htmx from "htmx.org"

window.htmx = htmx
window.Alpine = Alpine

Alpine.plugin(collapse)
Alpine.plugin(focus)
Alpine.plugin(morph)
Alpine.plugin(ui)
Alpine.plugin(validate)

document.addEventListener("DOMContentLoaded", () => Alpine.start())
