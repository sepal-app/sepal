import Alpine from "alpinejs"
import collapse from "@alpinejs/collapse"
import TomSelect from "tom-select"

window.TomSelect = TomSelect
window.htmx = require("htmx.org")
window.Alpine = Alpine

Alpine.plugin(collapse)
Alpine.start()
