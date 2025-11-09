import { type DirectiveCallback } from "alpinejs"

import SlimSelect, { Option } from "slim-select"

interface DirectiveExpression {
    url: string
}

const TaxonField: DirectiveCallback = (el, directive, { cleanup, evaluate }) => {
    const { url }: DirectiveExpression = evaluate(directive.expression)

    function onSearch(
        search: string,
        _currentData: Array<Partial<Option>>,
    ): Promise<Option[]> {
        // console.log("search: ", search)
        // console.log("currentData: ", currentData)
        return new Promise((resolve, reject) => {
            if (search.length < 2) {
                return reject("Search must be at least 2 characters")
            }
            const params = new URLSearchParams({ q: search, page_size: "6" })

            return (
                fetch(url + "?" + params.toString(), {
                    headers: { Accept: "application/json" },
                })
                    .then((response) => response.json())
                    // remove the current taxon from the completion list
                    // TODO: Allow filtering the parent from the taxon
                    // .then((data) => data.filter((t) => t.id.toString() == exclude))
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
            )
        })
    }

    const select = new SlimSelect({
        select: el,
        // settings: { focusSearch: true },
        events: { search: onSearch },
    })

    cleanup(() => {
        select.destroy()
    })
}

export default TaxonField
