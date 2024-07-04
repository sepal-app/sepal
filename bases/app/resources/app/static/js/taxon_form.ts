import Alpine from "alpinejs"
import TomSelect from "tom-select"
import TaxonField from "./TaxonField.ts"

document.addEventListener("alpine:init", () => {
    Alpine.data("taxonFormData", () => ({
        dirty: false,
        init() {
            // TODO: Can we generalize this for any form?
            const selector = "#taxon-form input, #taxon-form select, #taxon-form textarea"
            const inputs = document.querySelectorAll(selector)
            for (const input of inputs) {
                input.addEventListener("input", (el) => {
                    this.dirty = true
                })
            }
        },
    }))

    Alpine.directive("taxon-field", TaxonField)

    Alpine.directive("rank-field", (el, directive) => {
        new TomSelect(el, {
            itemClass: "sm:text-sm bg-white",
            optionClass: "sm:text-sm bg-white py-2 px-2",
            sortField: { field: "text" },
            selectOnTab: true,
        })
    })
})
