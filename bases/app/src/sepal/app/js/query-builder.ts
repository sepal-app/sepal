/**
 * Alpine.js component for the search query builder dropdown.
 *
 * Provides a visual interface for building search queries with
 * field selection, operators, and value input.
 */

export interface Field {
    key: string
    label: string
    type: "text" | "fts" | "date" | "number" | "count" | "enum" | "boolean" | "exists"
    values?: string[]
}

interface Operator {
    value: string
    label: string
    negated?: boolean
}

interface OperatorOptions {
    text: Operator[]
    date: Operator[]
    number: Operator[]
    enum: Operator[]
    single: Operator[]
}

const operatorOptions: OperatorOptions = {
    text: [
        { value: "", label: "Contains" },
        { value: "", label: "Does not contain", negated: true },
        { value: "=", label: "Equals" },
        { value: "=", label: "Does not equal", negated: true },
    ],
    date: [
        { value: "", label: "Equals" },
        { value: ">", label: "After" },
        { value: ">=", label: "On or after" },
        { value: "<", label: "Before" },
        { value: "<=", label: "On or before" },
    ],
    number: [
        { value: "", label: "Equals" },
        { value: "=", label: "Does not equal", negated: true },
        { value: ">", label: "Greater than" },
        { value: ">=", label: "At least" },
        { value: "<", label: "Less than" },
        { value: "<=", label: "At most" },
    ],
    enum: [
        { value: "", label: "Equals" },
        { value: "", label: "Does not equal", negated: true },
    ],
    single: [{ value: "", label: "Equals" }],
}

export function queryBuilder(fields: Field[], inputId: string) {
    return {
        open: false,
        fields,
        selectedField: "",
        selectedValue: "",
        selectedOpIndex: 0,

        get currentField(): Field | undefined {
            return this.fields.find((f) => f.key === this.selectedField)
        },

        get currentFieldType(): string {
            return this.currentField?.type || "text"
        },

        get currentFieldValues(): string[] {
            return this.currentField?.values || []
        },

        get availableOps(): Operator[] {
            const type = this.currentFieldType
            if (type === "text" || type === "fts") return operatorOptions.text
            if (type === "date") return operatorOptions.date
            if (type === "number" || type === "count") return operatorOptions.number
            if (type === "enum") return operatorOptions.enum
            return operatorOptions.single
        },

        get selectedOp(): Operator | undefined {
            return this.availableOps[this.selectedOpIndex]
        },

        get needsValue(): boolean {
            const type = this.currentFieldType
            return type !== "boolean" && type !== "exists"
        },

        get canAddFilter(): boolean {
            if (!this.selectedField) return false
            if (!this.needsValue) return true
            return this.selectedValue.trim() !== ""
        },

        onFieldChange() {
            this.selectedValue = ""
            this.selectedOpIndex = 0
        },

        addFilter() {
            if (!this.canAddFilter) return

            const input = document.getElementById(inputId) as HTMLInputElement
            if (!input) return

            const currentQ = input.value.trim()
            const op = this.selectedOp

            let filter = ""
            if (op?.negated) filter += "-"
            filter += this.selectedField

            if (this.needsValue && this.selectedValue) {
                const val = this.selectedValue.includes(" ")
                    ? '"' + this.selectedValue + '"'
                    : this.selectedValue
                filter += ":" + (op?.value || "") + val
            }

            input.value = currentQ ? currentQ + " " + filter : filter

            // Reset state
            this.selectedField = ""
            this.selectedValue = ""
            this.selectedOpIndex = 0
            this.open = false

            // Trigger form submission
            input.form?.requestSubmit()
        },
    }
}

/**
 * Alpine.js component for the "Only taxa with accessions" checkbox.
 *
 * Syncs checkbox state with the presence of `accessions:>0` in the search query.
 */
export function accessionsOnlyFilter(inputId: string, initialChecked: boolean) {
    return {
        checked: initialChecked,

        toggle() {
            const input = document.getElementById(inputId) as HTMLInputElement
            if (!input) return

            let q = input.value.trim()

            if (this.checked) {
                // Remove the filter
                q = q.replace(/\s*accessions:>0\s*/g, " ").trim()
                this.checked = false
            } else {
                // Add the filter
                q = q ? q + " accessions:>0" : "accessions:>0"
                this.checked = true
            }

            input.value = q
            input.form?.requestSubmit()
        },
    }
}
