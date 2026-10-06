// The rows ticked on a list page, by id. Each row's checkbox is bound to
// `selected` with x-model, so rows appended by scrolling show their state.
type ListSelection = {
    selected: string[]
    loaded: number
    $root: HTMLElement
    clear(): void
    allLoaded(): boolean
    countLoaded(): void
}

const rowBoxes = "input[data-select-row]"

export function listSelection(toolbarId: string) {
    return {
        selected: [] as string[],
        loaded: 0,
        init(this: ListSelection) {
            this.countLoaded()
        },
        countLoaded(this: ListSelection) {
            this.loaded = this.$root.querySelectorAll(rowBoxes).length
        },
        clear(this: ListSelection) {
            this.selected = []
        },
        allLoaded(this: ListSelection) {
            return this.loaded > 0 && this.selected.length === this.loaded
        },
        toggleAll(this: ListSelection) {
            if (this.allLoaded()) {
                this.clear()
                return
            }
            this.selected = Array.from(
                this.$root.querySelectorAll<HTMLInputElement>(rowBoxes),
                (el) => el.value,
            )
        },
        // A bulk action succeeded: close its dialog and reload the list
        // through the toolbar, which keeps the search and the sort.
        applied(this: ListSelection) {
            this.$root
                .querySelectorAll<HTMLDialogElement>("dialog[open]")
                .forEach((d) => d.close())
            this.clear()
            const toolbar = document.getElementById(toolbarId)
            if (toolbar instanceof HTMLFormElement) toolbar.requestSubmit()
        },
    }
}
