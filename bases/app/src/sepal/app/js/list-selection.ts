// The rows ticked on a list page, by id. Each row's checkbox is bound to
// `selected` with x-model, so rows appended by scrolling show their state.
type ListSelection = {
    selected: string[]
    loaded: number
    $root: HTMLElement
    $nextTick(callback: () => void): Promise<void>
    clear(): void
    allLoaded(): boolean
    countLoaded(): void
}

const rowBoxes = "input[data-select-row]"

// Each bulk dialog has a slot for a failure's message, which the server
// swaps in; ui.bulk/dialog renders it.
const errorSlot = ".spl-bulk-error"

// Back to how the page rendered it: no values, no errors.
function resetDialog(dialog: HTMLDialogElement) {
    dialog.querySelectorAll("form").forEach((form) => form.reset())
    dialog.querySelectorAll(`ul.spl-error, ${errorSlot}`).forEach((el) => {
        el.replaceChildren()
    })
    dialog.querySelectorAll("[aria-invalid]").forEach((el) => {
        el.removeAttribute("aria-invalid")
    })
}

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
        // Without the message an earlier failure left in it.
        openDialog(this: ListSelection, id: string) {
            const dialog = document.getElementById(id)
            if (!(dialog instanceof HTMLDialogElement)) return
            dialog.querySelectorAll(errorSlot).forEach((el) => el.replaceChildren())
            dialog.showModal()
        },
        // A bulk action succeeded: close its dialog, empty every dialog for
        // the next selection, and reload the list through the toolbar, which
        // keeps the search and the sort. Focus goes to the search, since the
        // button that opened the dialog is hidden with the bar.
        applied(this: ListSelection) {
            const dialogs = this.$root.querySelectorAll<HTMLDialogElement>("dialog")
            dialogs.forEach((d) => d.close())
            dialogs.forEach(resetDialog)
            this.clear()
            const toolbar = document.getElementById(toolbarId)
            if (!(toolbar instanceof HTMLFormElement)) return
            toolbar.requestSubmit()
            this.$nextTick(() => {
                toolbar.querySelector<HTMLInputElement>("input[name=q]")?.focus()
            })
        },
    }
}
