import Alpine from "alpinejs"

import TaxonField from "~/js/taxon-field"

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)
})
