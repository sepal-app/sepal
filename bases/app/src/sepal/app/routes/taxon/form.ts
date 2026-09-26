import type { DirectiveCallback } from "alpinejs"

// Loaded by page.ts the first time a taxon form element or hook needs it.

// Set by the rank field while it is on the page. The create form asks the
// server what rank a name implies and drops the answer into #rank-guess;
// that element calls applyRankGuess, which is this, or nothing on a page
// with no rank field.
let applyGuess: ((rank: string) => void) | null = null

export function applyRankGuess(rank: string) {
    if (applyGuess) applyGuess(rank)
}

// The parent the name implies, when the server is sure enough to name one.
// Blank means it is not, which must leave the field alone rather than clear
// it. The picker is a <sepal-combobox>, which takes a record through
// setSelection — silently, so x-suggestable does not read it as a change you
// made and stop suggesting.
export function applyParentSuggestion(body: string) {
    const select = document.getElementById("parent-id") as
        | (HTMLElement & {
              setSelection?: (
                  option: { id: string; text: string } | null,
                  opts?: { silent?: boolean },
              ) => void
              value?: string
          })
        | null
    if (!select || select.dataset.touched === "true" || !select.setSelection) return

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
            apply(select, () => select.setSelection!(null, { silent: true }))
            delete select.dataset.suggested
        }
        return
    }

    const value = String(suggestion.id)
    if (select.value === value) return
    apply(select, () =>
        select.setSelection!({ id: value, text: suggestion.text }, { silent: true }),
    )
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

// A field a suggestion may fill in, until it is set by hand. Changes the
// suggestion itself makes are marked and do not count.
export const suggestable: DirectiveCallback = (el, {}, { cleanup }) => {
    const mark = () => {
        if (el.dataset.applying !== "true") el.dataset.touched = "true"
    }
    el.addEventListener("change", mark)
    cleanup(() => el.removeEventListener("change", mark))
}

// A plain <select> now. It was wrapped in a widget purely for looks, and
// the form's other enum controls never were — so this is one fewer thing
// between a guess and the field it writes.
export const rankField: DirectiveCallback = (el, {}, { cleanup }) => {
    const select = el as HTMLSelectElement

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
        select.value = rank
        applying = false
    }

    cleanup(() => {
        el.removeEventListener("change", markTouched)
        applyGuess = null
    })
}
