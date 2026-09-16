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
        applyParentSuggestion: (json: string) => void
    }
}

window.applyRankGuess = (rank: string) => {
    if (applyGuess) applyGuess(rank)
}

// The parent the name implies, when the server is sure enough to name one.
// Blank means it is not, which must leave the field alone rather than clear
// it. SlimSelect owns the control, so the option is added through it — and it
// assigns itself to the select as `slim`, which is the only way in from here.
window.applyParentSuggestion = (body: string) => {
    const select = document.getElementById(
        "parent-id",
    ) as (HTMLSelectElement & { slim?: SlimSelect }) | null
    if (!select || select.dataset.touched === "true" || !select.slim) return

    let suggestion: { id: number; text: string } | null = null
    if (body) {
        try {
            suggestion = JSON.parse(body)
        } catch {
            return
        }
    }

    // A parent this filled in has to come back out when the name stops
    // implying it. Renaming from `Acer palmatum 'X'` to something the server
    // cannot resolve would otherwise leave the maple behind and save the new
    // taxon under it. A parent set by hand is never touched.
    if (!suggestion || !suggestion.id) {
        if (select.dataset.suggested === "true") {
            apply(select, () => {
                select.slim!.setData([{ text: "", value: "", placeholder: true }])
            })
            delete select.dataset.suggested
        }
        return
    }

    const value = String(suggestion.id)
    if (select.value === value) return
    const text = suggestion.text
    apply(select, () => {
        select.slim!.setData([
            { text: "", value: "", placeholder: true },
            { text, value },
        ])
        select.slim!.setSelected(value, false)
    })
    select.dataset.suggested = "true"
}

// x-suggestable reads `change` to decide a field was set by hand. Changes made
// here are not by hand.
function apply(el: HTMLElement, f: () => void) {
    el.dataset.applying = "true"
    try {
        f()
    } finally {
        delete el.dataset.applying
    }
}

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)

    // A field a suggestion may fill in, until it is set by hand. Changes the
    // suggestion itself makes are marked and do not count.
    Alpine.directive("suggestable", (el, {}, { cleanup }) => {
        const mark = () => {
            if (el.dataset.applying !== "true") el.dataset.touched = "true"
        }
        el.addEventListener("change", mark)
        cleanup(() => el.removeEventListener("change", mark))
    })

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
