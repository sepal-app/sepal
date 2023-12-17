import TomSelect from "tom-select"
import TaxonField from "./TaxonField"

document.addEventListener("alpine:init", () => {
    Alpine.directive("taxon-field", TaxonField)
})
