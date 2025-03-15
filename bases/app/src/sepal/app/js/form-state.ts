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

    const inputs = el.querySelectorAll("input, select, textarea")

    const handler = () => {
        data.dirty = true
        data.valid = el.checkValidity()
    }

    for (const input of inputs) {
        input.addEventListener("input", handler)
    }

    cleanup(() => {
        for (const input of inputs) {
            input.removeEventListener("input", handler)
        }
    })
}
