import Alpine from "alpinejs"
import * as htmx from "htmx.org"

import TaxonField from "./TaxonField"

window.htmx = htmx

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)
})
