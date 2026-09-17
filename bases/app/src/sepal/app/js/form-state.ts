// import Alpine from "alpinejs"

import type { DirectiveData, DirectiveUtilities } from "alpinejs"

type FormStateData = {
    dirty: boolean
    valid: boolean
}

export default (
    el: HTMLElement,
    _directive: DirectiveData,
    { Alpine, cleanup }: DirectiveUtilities,
) => {
    if (!(el instanceof HTMLFormElement)) {
        console.warn("Form state element is not a form")
        return
    }
    const data = Alpine.$data(el) as FormStateData
    data.dirty = false
    data.valid = true

    // `change` as well as `input`: a picker commits by writing its value and
    // dispatching `change`, so an `input` listener never sees a taxon,
    // supplier or location being chosen. Save is bound to `valid`, so the
    // button stayed disabled on a form that was filled in.
    const events = ["input", "change"]

    function addListeners() {
        const inputs = findInputs()
        for (const input of inputs) {
            rememberInitial(input)
            for (const event of events) {
                input.addEventListener(event, handler)
            }
        }
        el.addEventListener("form-state.dirty", handler)
    }

    function removeListeners() {
        const inputs = findInputs()
        for (const input of inputs) {
            for (const event of events) {
                input.removeEventListener(event, handler)
            }
        }
        el.removeEventListener("form-state.dirty", handler)
    }

    const observer = new MutationObserver((_mutationList, _) => {
        removeListeners()
        addListeners()
    })

    observer.observe(el, {
        childList: true,
        subtree: true,
    })

    // What a control held when the page arrived, so an edited one can be
    // marked. Recorded on the element rather than in a closure: the observer
    // below rebinds listeners whenever the form's markup changes, and a
    // control swapped in by HTMX arrives with its own starting value.
    function rememberInitial(input: Element) {
        if (!(input instanceof HTMLElement)) return
        if (input.dataset.initialValue !== undefined) return
        input.dataset.initialValue = currentValue(input)
    }

    function currentValue(input: Element) {
        if (input instanceof HTMLInputElement && (input.type === "checkbox" || input.type === "radio")) {
            return String(input.checked)
        }
        const v = (input as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value
        return v == null ? "" : String(v)
    }

    // Which fields a save is about to write. The form as a whole already knew
    // it was dirty; this says which parts of it are.
    function markEdited(input: Element) {
        if (!(input instanceof HTMLElement)) return
        const initial = input.dataset.initialValue
        if (initial === undefined) return
        input.classList.toggle("spl-input--edited", currentValue(input) !== initial)
    }

    const handler = (event?: Event) => {
        data.dirty = true
        data.valid = el.checkValidity()
        const target = event?.target
        if (target instanceof Element) markEdited(target)
    }

    function findInputs() {
        return el.querySelectorAll("input, select, textarea")
    }

    addListeners()

    cleanup(() => {
        observer.disconnect()
        removeListeners()
    })
}
