import { type DirectiveCallback } from "alpinejs"
import TomSelect from "tom-select"

interface DirectiveExpression {
    url: string
}

const AccessionField: DirectiveCallback = (el, directive, { cleanup, evaluate }) => {
    const { url }: DirectiveExpression = evaluate(directive.expression)

    function load(query: string, callback: () => void) {
        // We need to clear the options so that previous options don't get
        // merged with the new ones
        // @ts-ignore
        this.clearOptions()
        const params = new URLSearchParams({ q: query, page_size: "6" })
        fetch(url + "?" + params.toString(), {
            headers: { Accept: "application/json" },
        })
            .then((response) => response.json())
            .then(callback)
            .catch((e) => {
                console.error(e)
                // @ts-ignore
                callback()
            })
    }

    const tomselect = new TomSelect(el as HTMLInputElement, {
        itemClass: "sm:text-sm bg-white",
        loadingClass: "",
        maxItems: 1,
        openOnFocus: false,
        searchField: [],
        valueField: "id",
        labelField: "text",
        optionClass: "sm:text-sm bg-white py-2 px-3",
        selectOnTab: true,
        load: load,
    })

    cleanup(() => {
        tomselect.destroy()
    })
}

export default AccessionField
