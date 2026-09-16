import Alpine from "alpinejs"
import "htmx.org"
import SlimSelect from "slim-select"

import TaxonField from "~/js/taxon-field"

// Set by the rank field while it is on the page. The create form asks the
// server what rank a name implies and drops the answer into #rank-guess;
// that element calls applyRankGuess, which is this, or nothing on a page
// with no rank field.
let applyGuess: ((rank: string) => void) | null = null

declare global {
    interface Window {
        applyRankGuess: (rank: string) => void
    }
}

window.applyRankGuess = (rank: string) => {
    if (applyGuess) applyGuess(rank)
}

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)

    Alpine.directive("rank-field", (el, {}, { cleanup }) => {
        const select = new SlimSelect({ select: el })

        // Set the rank yourself and the guessing stops for good. The flag
        // lives on the element rather than in a closure so the guard survives
        // anything that re-reads the DOM.
        let applying = false
        const markTouched = () => {
            if (!applying) el.dataset.touched = "true"
        }
        el.addEventListener("change", markTouched)

        applyGuess = (rank: string) => {
            if (!rank || el.dataset.touched === "true") return
            // The name is typed left to right, so an early guess is usually
            // wrong and a later one corrects it. That is fine: this only ever
            // writes a field you have not set.
            applying = true
            select.setSelected(rank, false)
            applying = false
        }

        cleanup(() => {
            el.removeEventListener("change", markTouched)
            applyGuess = null
            select.destroy()
        })
    })
})
