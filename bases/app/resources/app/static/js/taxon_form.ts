import Alpine from "alpinejs"
import TomSelect from "tom-select"
import TaxonField from "./TaxonField.ts"

document.addEventListener("alpine:init", () => {
    console.log("taxon_form/alpine:init")
    Alpine.data("taxonFormData", () => ({
        dirty: false,
        init() {
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
    const rankSelect = document.querySelector("select#rank")
    if (rankSelect) {
        new TomSelect("select#rank", {
            itemClass: "sm:text-sm bg-white",
            optionClass: "sm:text-sm bg-white py-2 px-3",
            sortField: { field: "text" },
            selectOnTab: true,
        })
    }
})
