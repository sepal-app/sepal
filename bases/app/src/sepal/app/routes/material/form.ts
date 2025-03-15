import Alpine from "alpinejs"
import TomSelect from "tom-select"

import AccessionField from "~/js/accession-field"
import LocationField from "~/js/location-field"

document.addEventListener("alpine:init", () => {
    Alpine.directive("accession-field", AccessionField)
    Alpine.directive("location-field", LocationField)

    Alpine.directive("material-status-field", (el, directive) => {
        new TomSelect(el as HTMLInputElement, {
            itemClass: "sm:text-sm bg-white",
            optionClass: "sm:text-sm bg-white py-2 px-3",
            selectOnTab: true,
        })
    })
    Alpine.directive("material-type-field", (el, directive) => {
        new TomSelect(el as HTMLInputElement, {
            itemClass: "sm:text-sm bg-white",
            optionClass: "sm:text-sm bg-white py-2 px-2",
            selectOnTab: true,
        })
    })
})
