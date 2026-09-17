/**
 * <sepal-combobox> — a record picker over a search endpoint.
 *
 * Why a custom element rather than a library. The options come from the server
 * as you type, and neither Alpine UI's combobox nor the component libraries we
 * looked at model that: they all filter a local array. Driving Alpine UI's from
 * a fetch corrupted the typing, because it re-renders the input from its own
 * model and that rewrite races the keystrokes — "bed" arrived as "edb". The
 * element owns its input outright, so nothing else writes to it.
 *
 * It also re-initialises itself when htmx swaps markup in, through
 * connectedCallback — and it keeps no hidden <select> for anything else to
 * watch, which is what made a picker rebuild and close mid-search when htmx or
 * the edited-field mark touched its class list.
 *
 * Light DOM on purpose. The spl- layer styles it, <label for> names it, and the
 * server renders the markup the way it renders every other field.
 *
 * Ideas borrowed: form association through ElementInternals, and reverting the
 * text on Escape, from baredom's x-combobox; keeping the typed text and the
 * committed value as separate things, from Web Awesome's combobox.
 */

type Option = { id: string; text: string }

const MIN_QUERY = 2
const DEBOUNCE_MS = 250

export class SepalCombobox extends HTMLElement {
    static formAssociated = true

    private internals: ElementInternals
    private input!: HTMLInputElement
    private listbox!: HTMLUListElement
    private button!: HTMLButtonElement
    private status!: HTMLElement

    private options: Option[] = []
    private active = -1
    private open = false

    /** The record chosen. Only an explicit selection changes it — typing does
     *  not, so the field never submits a half-typed name. */
    private selected: Option | null = null

    private timer: number | undefined
    private pending: AbortController | undefined

    /** Every row, for a picker whose list is fixed and arrives with the page —
     *  a timezone is one of about four hundred and none of them change. */
    private local: HTMLElement[] | null = null

    constructor() {
        super()
        this.internals = this.attachInternals()
    }

    /** The record chosen, as the id the form submits. htmx reads this for
     *  hx-include, which is how a suggestion endpoint gets told what to
     *  suggest from. */
    get value() {
        return this.selected ? this.selected.id : ""
    }

    get name() {
        return this.getAttribute("name") ?? ""
    }

    /**
     * Set or clear the record from outside.
     *
     * A suggestion endpoint fills this in while you type a name. `silent`
     * keeps that from counting as a change you made: x-suggestable watches for
     * one to decide the field was set by hand, and stops suggesting after.
     */
    setSelection(option: Option | null, { silent = false } = {}) {
        this.selected = option
        this.internals.setFormValue(option ? option.id : "")
        this.showText(option)
        if (!silent) {
            this.input.dispatchEvent(new Event("change", { bubbles: true }))
        }
    }

    connectedCallback() {
        const input = this.querySelector<HTMLInputElement>("[role=combobox]")
        const listbox = this.querySelector<HTMLUListElement>("[role=listbox]")
        const button = this.querySelector<HTMLButtonElement>(".spl-combobox-button")
        const status = this.querySelector<HTMLElement>("[data-combobox-status]")
        if (!input || !listbox || !button || !status) return
        this.input = input
        this.listbox = listbox
        this.button = button
        this.status = status

        // No url means the rows are already here and the search is a filter.
        if (!this.dataset.url) {
            this.local = Array.from(
                listbox.querySelectorAll<HTMLElement>("[role=option]"),
            )
        }

        const id = this.dataset.value
        const text = this.dataset.text
        this.selected = id && text ? { id, text } : null
        this.commit(this.selected, { silent: true })

        input.addEventListener("focus", this.onFocus)
        input.addEventListener("input", this.onInput)
        input.addEventListener("keydown", this.onKeydown)
        input.addEventListener("blur", this.onBlur)
        button.addEventListener("mousedown", this.onButton)
        listbox.addEventListener("mousedown", this.onListMousedown)
    }

    disconnectedCallback() {
        window.clearTimeout(this.timer)
        this.pending?.abort()
    }

    formResetCallback() {
        this.commit(null)
        this.input.value = ""
    }

    // ── value ────────────────────────────────────────────────────────────────

    /** Write the value the form submits, and say whether the field is filled. */
    private commit(option: Option | null, { silent = false } = {}) {
        this.selected = option
        this.internals.setFormValue(option ? option.id : "")
        if (this.dataset.required !== undefined && !option) {
            this.internals.setValidity(
                { valueMissing: true },
                `${this.dataset.label ?? "This field"} is required`,
                this.input,
            )
        } else {
            this.internals.setValidity({})
        }
        if (silent) return
        this.input.value = option ? option.text : ""
        // The form's dirty and edited tracking listens on real controls, and
        // its validity comes from the form. Both want to hear about this.
        this.input.dispatchEvent(new Event("change", { bubbles: true }))
    }

    /** Write the text without announcing it, for a value set from outside. */
    private showText(option: Option | null) {
        this.input.value = option ? option.text : ""
    }

    // ── events ───────────────────────────────────────────────────────────────

    private onFocus = () => {
        // The caret goes to the end of a record you picked: it is a value, not
        // a sentence to edit in the middle of.
        //
        // Only when something is committed, and the length is read inside the
        // frame rather than captured outside it. Doing neither put the caret
        // at position 0 of an empty field one frame after focus, so every
        // character after the first inserted in front of the last: typing
        // "bed 0" gave "ed 0b".
        if (!this.selected) return
        requestAnimationFrame(() => {
            const end = this.input.value.length
            this.input.setSelectionRange(end, end)
        })
    }

    private onBlur = () => {
        // Revert to the committed record. Text that matches nothing would
        // otherwise sit in the field looking like a selection.
        window.setTimeout(() => {
            if (this.contains(document.activeElement)) return
            this.close()
            this.input.value = this.selected ? this.selected.text : ""
        }, 120)
    }

    private onButton = (event: MouseEvent) => {
        event.preventDefault()
        this.input.focus()
        if (this.open) this.close()
        else this.search(this.input.value)
    }

    private onInput = () => {
        this.search(this.input.value)
    }

    private onListMousedown = (event: MouseEvent) => {
        const li = (event.target as HTMLElement).closest("[role=option]")
        if (!(li instanceof HTMLElement) || li.hasAttribute("aria-disabled")) return
        event.preventDefault()
        const index = Number(li.dataset.index)
        this.choose(index)
    }

    private onKeydown = (event: KeyboardEvent) => {
        switch (event.key) {
            case "ArrowDown":
                event.preventDefault()
                if (!this.open) this.search(this.input.value)
                else this.moveTo(this.active + 1)
                break
            case "ArrowUp":
                event.preventDefault()
                this.moveTo(this.active - 1)
                break
            case "Home":
                if (!this.open) return
                event.preventDefault()
                this.moveTo(0)
                break
            case "End":
                if (!this.open) return
                event.preventDefault()
                this.moveTo(this.options.length - 1)
                break
            case "Enter":
                if (!this.open || this.active < 0) return
                event.preventDefault()
                this.choose(this.active)
                break
            case "Escape":
                if (!this.open) return
                event.preventDefault()
                this.close()
                this.input.value = this.selected ? this.selected.text : ""
                break
            case "Backspace":
                // A committed record clears whole. It is one thing, so removing
                // it removes the record — not the last letter of its name,
                // which would leave the field holding a name that matches
                // nothing while a record is still selected underneath. Once you
                // have typed over it, backspace is backspace again.
                if (!this.selected) return
                if (this.input.value !== this.selected.text) return
                event.preventDefault()
                this.commit(null)
                this.close()
                break
        }
    }

    // ── searching ────────────────────────────────────────────────────────────

    private search(query: string) {
        window.clearTimeout(this.timer)
        if (this.local) {
            this.filterLocal(query)
            return
        }
        if (query.trim().length < MIN_QUERY) {
            this.pending?.abort()
            this.render(
                `<li class="spl-combobox-note" aria-disabled="true">Type ${MIN_QUERY} characters to search</li>`,
            )
            return
        }
        this.timer = window.setTimeout(() => void this.fetch(query), DEBOUNCE_MS)
    }

    /**
     * Narrow a list that is already here.
     *
     * No minimum and no debounce: there is no request to spare, and an empty
     * query showing everything is what makes a fixed list browsable — which is
     * the whole reason it was sent with the page.
     */
    private filterLocal(query: string) {
        const q = query.trim().toLowerCase()
        const matches = (this.local ?? []).filter(
            (li) => !q || (li.dataset.text ?? "").toLowerCase().includes(q),
        )
        this.listbox.replaceChildren(...matches)
        if (matches.length === 0) {
            const li = document.createElement("li")
            li.className = "spl-combobox-note"
            li.setAttribute("aria-disabled", "true")
            li.textContent = "No matches"
            this.listbox.append(li)
        }
        this.index()
    }

    private async fetch(query: string) {
        // One request in flight. An earlier answer arriving late would
        // otherwise replace the list for what is now in the field.
        this.pending?.abort()
        const controller = new AbortController()
        this.pending = controller

        const size = this.dataset.pageSize ?? "100"
        const params = new URLSearchParams({
            q: query,
            "page-size": size,
            options: "1",
        })
        try {
            const response = await fetch(`${this.dataset.url}?${params}`, {
                headers: { Accept: "text/html" },
                signal: controller.signal,
            })
            this.render(await response.text())
        } catch (e) {
            if ((e as Error).name === "AbortError") return
            console.error(e)
            this.render(
                '<li class="spl-combobox-note" aria-disabled="true">Could not search</li>',
            )
        }
    }

    // ── rendering ────────────────────────────────────────────────────────────

    /**
     * Take the rows the server rendered.
     *
     * The markup is the server's, so a scientific name keeps its serif and a
     * note reads the same as every other note in the app. This only numbers
     * the rows, so the keyboard has something to point at.
     */
    private render(markup: string) {
        this.listbox.innerHTML = markup
        this.index()
    }

    /** Number the rows, so the keyboard has something to point at. */
    private index() {
        const items = this.listbox.querySelectorAll<HTMLElement>("[role=option]")
        this.options = Array.from(items).map((li, index) => {
            // Named off the field, not the element's id, which it has none of.
            li.id = `${this.dataset.name}-option-${index}`
            li.dataset.index = String(index)
            return {
                id: li.dataset.value ?? "",
                text: li.dataset.text ?? li.textContent ?? "",
            }
        })
        this.active = this.options.length > 0 ? 0 : -1
        this.announce(this.summary())
        this.show()
        this.paint()
    }

    /** What a reader who cannot see the list is told. */
    private summary() {
        const note = this.listbox.querySelector(".spl-combobox-note")
        if (this.options.length === 0) return note?.textContent ?? "No matches"
        const n = this.options.length
        return `${n} result${n === 1 ? "" : "s"}${note ? `. ${note.textContent}` : ""}`
    }

    private moveTo(index: number) {
        if (this.options.length === 0) return
        const last = this.options.length - 1
        this.active = index < 0 ? last : index > last ? 0 : index
        this.paint()
    }

    private choose(index: number) {
        const option = this.options[index]
        if (!option) return
        this.commit(option)
        this.close()
    }

    /** Mark the active option and point the input at it. */
    private paint() {
        const items = this.listbox.querySelectorAll<HTMLElement>("[role=option]")
        items.forEach((li, index) => {
            const active = index === this.active
            li.classList.toggle("spl-combobox-option--active", active)
            if (active) {
                this.input.setAttribute("aria-activedescendant", li.id)
                li.scrollIntoView({ block: "nearest" })
            }
        })
        if (this.active < 0) this.input.removeAttribute("aria-activedescendant")
    }

    private show() {
        this.open = true
        this.listbox.hidden = false
        this.input.setAttribute("aria-expanded", "true")
    }

    private close() {
        this.open = false
        this.listbox.hidden = true
        this.input.setAttribute("aria-expanded", "false")
        this.input.removeAttribute("aria-activedescendant")
    }

    /** Say what happened, for a reader who cannot see the list appear. */
    private announce(message: string) {
        this.status.textContent = message
    }
}

export function defineCombobox() {
    if (!customElements.get("sepal-combobox")) {
        customElements.define("sepal-combobox", SepalCombobox)
    }
}
