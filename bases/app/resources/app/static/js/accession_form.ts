import Alpine from "alpinejs"

import TaxonField from "./TaxonField.ts"

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)
})
