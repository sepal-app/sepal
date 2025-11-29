import Alpine from "alpinejs"
import "htmx.org"
import SlimSelect from "slim-select"

import TaxonField from "~/js/taxon-field"

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)

    Alpine.directive("rank-field", (el, {}, { cleanup }) => {
        const select = new SlimSelect({ select: el })
        cleanup(() => select.destroy())
    })
})
