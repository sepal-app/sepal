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

    function addListeners() {
        const inputs = findInputs()
        for (const input of inputs) {
            input.addEventListener("input", handler)
        }
        el.addEventListener("form-state.dirty", handler)
    }

    function removeListeners() {
        const inputs = findInputs()
        for (const input of inputs) {
            input.removeEventListener("input", handler)
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

    const handler = () => {
        data.dirty = true
        data.valid = el.checkValidity()
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
