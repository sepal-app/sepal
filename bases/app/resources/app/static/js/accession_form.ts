import TomSelect from "tom-select"
import { createApp } from "vue"

import TaxonField from "./TaxonField.vue"

const app = createApp({}).component("taxon-field", TaxonField).mount("#accession-form")
