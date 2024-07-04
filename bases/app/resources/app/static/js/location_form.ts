import Alpine from "alpinejs"

document.addEventListener("alpine:init", () => {
    Alpine.data("locationFormData", () => ({
        dirty: false,
        init() {
            // TODO: Can we generalize this for any form?
            const selector =
                "#location-form input, #location-form select, #location-form textarea"
            const inputs = document.querySelectorAll(selector)
            for (const input of inputs) {
                input.addEventListener("input", (el) => {
                    this.dirty = true
                })
            }
        },
    }))
})
