import Alpine from "alpinejs"
import TomSelect from "tom-select"
import TaxonField from "~/js/taxon-field"

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)

    Alpine.directive("rank-field", (el, { expression }) => {
        const options = expression ? JSON.parse(expression) : {}
        new TomSelect(el as HTMLInputElement, {
            itemClass: "sm:text-sm bg-white",
            optionClass: "sm:text-sm bg-white py-2 px-2",
            sortField: [{ field: "text" }],
            selectOnTab: true,
            ...options,
        })
    })
})
