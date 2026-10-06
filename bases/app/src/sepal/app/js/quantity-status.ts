// Quantity is 0 and read-only while the status is one that holds no plants.
// Switching back restores what the field held before.
type QuantityStatus = {
    locked: boolean
    kept: string
    $refs: { status: HTMLSelectElement; quantity: HTMLInputElement }
    $root: HTMLElement
    $nextTick: (f: () => void) => void
    sync(): void
}

export function quantityStatus(living: string[]) {
    return {
        locked: false,
        kept: "",
        init(this: QuantityStatus) {
            this.sync()
            // A reset puts both controls back to their rendered values
            // without a change event.
            this.$root.closest("form")?.addEventListener("reset", () =>
                this.$nextTick(() => {
                    this.locked = false
                    this.sync()
                }),
            )
        },
        sync(this: QuantityStatus) {
            const quantity = this.$refs.quantity
            const isLiving = living.includes(this.$refs.status.value)
            if (!isLiving && !this.locked) {
                this.kept = quantity.value
                quantity.value = "0"
                quantity.dispatchEvent(new Event("input", { bubbles: true }))
                this.locked = true
            } else if (isLiving && this.locked) {
                quantity.value = this.kept
                quantity.dispatchEvent(new Event("input", { bubbles: true }))
                this.locked = false
            }
        },
    }
}
