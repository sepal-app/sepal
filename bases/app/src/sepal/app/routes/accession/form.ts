// Loaded by page.ts the first time the accession form's hook is called.

// Picking a taxon whose rank is governed by the cultivated plant code — a
// cultivar, a Group or a grex — implies the plant is cultivated. Fill it in
// until the field is set by hand, then leave it alone. A blank answer means
// the rank implies nothing, which must not clear a value.
export function applyProvenanceSuggestion(provenance: string) {
    if (!provenance) return
    const field = document.getElementById(
        "provenance-type",
    ) as HTMLSelectElement | null
    if (!field || field.dataset.touched === "true") return
    field.value = provenance
    field.dispatchEvent(new Event("input", { bubbles: true }))
}
