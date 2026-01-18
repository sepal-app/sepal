/**
 * Timezone field directive using SlimSelect.
 *
 * All timezone options are server-rendered; SlimSelect provides client-side filtering.
 *
 * Usage:
 *   <select x-timezone-field name="timezone">
 *     <option value="">Select a timezone...</option>
 *     <option value="America/New_York">(UTC-05:00) America/New_York</option>
 *     ...
 *   </select>
 */

import { type DirectiveCallback } from "alpinejs"
import SlimSelect, { Option } from "slim-select"

/**
 * Fuzzy search filter - all search words must appear somewhere in the option.
 * Searches both text and value, normalizes underscores/slashes to spaces.
 * e.g., "america new york" matches "(UTC-05:00) America/New_York"
 */
function fuzzySearchFilter(option: Option, search: string): boolean {
    // Normalize both text and search: lowercase, treat underscores and slashes as spaces
    const text = (option.text + " " + (option.value || ""))
        .toLowerCase()
        .replace(/[_/]/g, " ")
    const normalizedSearch = search.toLowerCase().replace(/[_/]/g, " ")
    const searchWords = normalizedSearch.split(/\s+/).filter(w => w.length > 0)

    // All search words must appear in the text
    return searchWords.every(word => text.includes(word))
}

const TimezoneField: DirectiveCallback = (el, _directive, { cleanup }) => {
    const select = new SlimSelect({
        select: el as HTMLSelectElement,
        settings: {
            allowDeselect: false,
            placeholderText: "Select a timezone...",
            searchPlaceholder: "Search timezones...",
            searchText: "No timezones found",
        },
        events: {
            searchFilter: fuzzySearchFilter,
            afterChange: () => {
                // Notify form-state of changes for dirty tracking
                const form = (el as HTMLSelectElement).form
                form?.dispatchEvent(new CustomEvent("form-state.dirty"))
            },
        },
    })

    cleanup(() => select.destroy())
}

export default TimezoneField
