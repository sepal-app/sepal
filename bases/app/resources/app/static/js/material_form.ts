import Alpine from "alpinejs"
import TomSelect from "tom-select"

import AccessionField from "./AccessionField.ts"
import LocationField from "./LocationField.ts"

document.addEventListener("alpine:init", () => {
    Alpine.data("materialFormData", () => ({
        dirty: false,
        init() {
            // TODO: Can we generalize this for any form?
            const selector =
                "#material-form input, #material-form select, #material-form textarea"
            const inputs = document.querySelectorAll(selector)
            for (const input of inputs) {
                input.addEventListener("input", (el) => {
                    this.dirty = true
                })
            }
        },
    }))
    Alpine.directive("accession-field", AccessionField)
    Alpine.directive("location-field", LocationField)

    Alpine.directive("material-status-field", (el, directive) => {
        new TomSelect(el, {
            itemClass: "sm:text-sm bg-white",
            optionClass: "sm:text-sm bg-white py-2 px-3",
            selectOnTab: true,
        })
    })
    Alpine.directive("material-type-field", (el, directive) => {
        new TomSelect(el, {
            itemClass: "sm:text-sm bg-white",
            optionClass: "sm:text-sm bg-white py-2 px-2",
            selectOnTab: true,
        })
    })
})
