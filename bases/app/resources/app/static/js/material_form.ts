import Alpine from "alpinejs"

import AccessionField from "./AccessionField.ts"
import LocationField from "./LocationField.ts"

document.addEventListener("alpine:init", () => {
    Alpine.directive("accession-field", AccessionField)
    Alpine.directive("location-field", LocationField)
})
