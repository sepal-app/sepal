import Alpine from "alpinejs"
import * as htmx from "htmx.org"

import AccessionField from "./AccessionField.ts"
import LocationField from "./LocationField.ts"

window.htmx = htmx

document.addEventListener("alpine:init", () => {
    Alpine.directive("accession-field", AccessionField)
    Alpine.directive("location-field", LocationField)
})
