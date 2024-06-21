import TomSelect from "tom-select"
import TaxonField from "./TaxonField.ts"

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)
    const rankSelect = document.querySelector("select#rank")
    if (rankSelect) {
        new TomSelect("select#rank", {
            itemClass: "sm:text-sm bg-white",
            optionClass: "sm:text-sm bg-white py-2 px-3",
            sortField: { field: "text" },
            selectOnTab: true,
        })
    }
})
