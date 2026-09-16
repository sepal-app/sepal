import Alpine from "alpinejs"
import "htmx.org"

import TaxonField from "~/js/taxon-field"
import ContactField from "~/js/contact-field"
import LocationField from "~/js/location-field"

declare global {
    interface Window {
        applyProvenanceSuggestion: (provenance: string) => void
    }
}

// Picking a taxon whose rank is governed by the cultivated plant code — a
// cultivar, a Group or a grex — implies the plant is cultivated. Fill it in
// until the field is set by hand, then leave it alone. A blank answer means
// the rank implies nothing, which must not clear a value.
window.applyProvenanceSuggestion = (provenance: string) => {
    if (!provenance) return
    const field = document.getElementById(
        "provenance-type",
    ) as HTMLSelectElement | null
    if (!field || field.dataset.touched === "true") return
    field.value = provenance
    field.dispatchEvent(new Event("input", { bubbles: true }))
}

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)
    Alpine.directive("contact-field", ContactField)
    Alpine.directive("location-field", LocationField)

    // TODO: Create a generic directive like prevent-unsaved
    Alpine.data("accessionTabs", () => ({
        tabClicked(event) {
            const form = document.getElementById("accession-form")
            const { dirty } = Alpine.$data(form)

            // TODO: create a nice dialog box with "yes" and "no" buttons
            const msg =
                "You have unsaved changes.  \n\nAre you sure you want to navigate away from this page?"
            if (dirty && confirm(msg)) {
                event.preventDefault()
            }
        },
    }))
})
