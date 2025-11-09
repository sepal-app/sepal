import Alpine from "alpinejs"
import SlimSelect from "slim-select"

import AccessionField from "~/js/accession-field"
import LocationField from "~/js/location-field"

document.addEventListener("alpine:init", () => {
    Alpine.directive("accession-field", AccessionField)
    Alpine.directive("location-field", LocationField)

    Alpine.directive("material-status-field", (el, {}, { cleanup }) => {
        const select = new SlimSelect({ select: el })
        cleanup(() => select.destroy())
    })
    Alpine.directive("material-type-field", (el, {}, { cleanup }) => {
        const select = new SlimSelect({ select: el })
        cleanup(() => select.destroy())
    })
})
