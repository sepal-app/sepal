import Alpine from "alpinejs"
import collapse from "@alpinejs/collapse"
import focus from "@alpinejs/focus"
import ui from "@alpinejs/ui"
import morph from "@alpinejs/morph"
import validate from "@colinaut/alpinejs-plugin-simple-validate"
import "htmx.org"

console.log("auth/page.ts")

window.Alpine = Alpine

Alpine.plugin(collapse)
Alpine.plugin(focus)
Alpine.plugin(morph)
Alpine.plugin(ui)
Alpine.plugin(validate)

document.addEventListener("DOMContentLoaded", () => {
    Alpine.start()
    // For whatever reason Alpine doesn't seem to be removing x-cloak at the
    // right time so we were still getting FOUC.
    document.querySelectorAll("[x-cloak]").forEach((el) => el.removeAttribute("x-cloak"))
})
