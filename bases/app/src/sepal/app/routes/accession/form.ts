import Alpine from "alpinejs"

import TaxonField from "~/js/taxon_field"

document.addEventListener("alpine:init", () => {
    Alpine.data("accessionFormData", () => ({
        dirty: false,
        init() {
            // TODO: Can we generalize this for any form?
            const selector =
                "#accession-form input, #accession-form select, #accession-form textarea"
            const inputs = document.querySelectorAll(selector)
            for (const input of inputs) {
                input.addEventListener("input", (el) => {
                    this.dirty = true
                })
            }
        },
    }))
    Alpine.directive("taxon-field", TaxonField)
})
