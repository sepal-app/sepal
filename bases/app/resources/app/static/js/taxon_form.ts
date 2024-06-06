import TomSelect from "tom-select"
import TaxonField from "./TaxonField.ts"

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)

    const tomselect = new TomSelect("#rank", {
        sortField: { field: "text" },
        selectOnTab: true,
    })
})
