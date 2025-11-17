import Alpine from "alpinejs"

import TaxonField from "~/js/taxon-field"
import ContactField from "~/js/contact-field"

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)
    Alpine.directive("contact-field", ContactField)

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
