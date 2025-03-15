import Alpine from "alpinejs"

import AccessionField from "~/js/accession-field"
import LocationField from "~/js/location-field"
import TaxonField from "~/js/taxon-field"
import MaterialField from "~/js/material-field"

document.addEventListener("alpine:init", () => {
    Alpine.directive("accession-field", AccessionField)
    Alpine.directive("location-field", LocationField)
    Alpine.directive("material-field", MaterialField)
    Alpine.directive("taxon-field", TaxonField)
})
