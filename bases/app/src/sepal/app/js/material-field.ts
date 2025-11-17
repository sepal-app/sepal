import { type DirectiveCallback } from "alpinejs"
import SlimSelect, { Option } from "slim-select"

interface DirectiveExpression {
    url: string
}

const MaterialField: DirectiveCallback = (el, directive, { cleanup, evaluate }) => {
    const { url }: DirectiveExpression = evaluate(directive.expression)

    function onSearch(
        search: string,
        _currentData: Array<Partial<Option>>,
    ): Promise<Option[]> {
        return new Promise((resolve, reject) => {
            const params = new URLSearchParams({ q: search, page_size: "6" })
            fetch(url + "?" + params.toString(), {
                headers: { Accept: "application/json" },
            })
                .then((response) => response.json())
                .then((data) => {
                    if (!data || data.length === 0) {
                        return reject("No results found")
                    }
                    const options = data.map((d) => ({
                        text: d.text,
                        value: d.id,
                    }))
                    resolve(options)
                })
                .catch((e) => {
                    console.error(e)
                    return reject("Unknown error")
                })
        })
    }

    const select = new SlimSelect({
        select: el,
        events: {
            search: onSearch,

            afterChange: (newVal) => {
                // This is kind of a hack to get x-form-state for the form to set the
                // dirty state when the value changes
                el.form?.dispatchEvent(new CustomEvent("form-state.dirty"))
            },
        },
    })
    cleanup(() => select.destroy())
}

export default MaterialField
