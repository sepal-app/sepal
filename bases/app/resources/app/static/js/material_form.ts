import Alpine from "alpinejs"

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
})
