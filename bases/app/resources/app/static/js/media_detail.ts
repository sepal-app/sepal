import Alpine from "alpinejs"

import AccessionField from "./AccessionField.ts"
import LocationField from "./LocationField.ts"
import TaxonField from "./TaxonField.ts"
// import MaterialField from "./MaterialField.ts"

document.addEventListener("alpine:init", () => {
    Alpine.directive("accession-field", AccessionField)
    Alpine.directive("location-field", LocationField)
    // Alpine.directive("material-field", MaterialField)
    Alpine.directive("taxon-field", TaxonField)
})
