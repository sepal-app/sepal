import Alpine from "alpinejs"

import AccessionField from "~/js/accession_field"
import LocationField from "~/js/location_field"
import TaxonField from "~/js/taxon_field"
import MaterialField from "~/js/material_field"

document.addEventListener("alpine:init", () => {
    Alpine.directive("accession-field", AccessionField)
    Alpine.directive("location-field", LocationField)
    Alpine.directive("material-field", MaterialField)
    Alpine.directive("taxon-field", TaxonField)
})
