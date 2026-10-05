// Places a list's column picker below its header button when it opens. A
// popover sits in the top layer with no anchor of its own.
export function columnPicker() {
    return {
        place(this: { $el: HTMLElement }, event: ToggleEvent) {
            if (event.newState !== "open") return
            const panel = this.$el
            const button = document.querySelector<HTMLElement>(
                `[popovertarget="${panel.id}"]`,
            )
            if (!button) return
            const rect = button.getBoundingClientRect()
            panel.style.top = `${rect.bottom + 4}px`
            panel.style.left = `${Math.max(8, rect.right - panel.offsetWidth)}px`
        },
    }
}
