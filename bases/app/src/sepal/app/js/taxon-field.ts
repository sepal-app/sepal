import { type DirectiveCallback } from "alpinejs"

import SlimSelect, { Option } from "slim-select"

import { debounceSearch } from "./debounce-search"

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
            // page-size, not page_size: the routes decode a dashed key, so an
            // underscore never matches and the request falls back to the list
            // default. Twenty-five because ten hid records: a garden with more
            // locations than this matching what was typed simply never saw the
            // rest, and the answer to a picker that cannot find a location is
            // to create it again.
            const params = new URLSearchParams({ q: search, "page-size": "25" })

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
                        // Same as location-field: SlimSelect keeps the
                        // selected option and appends these, so resolving the
                        // selected record again leaves two options for the
                        // one taxon.
                        const options = data
                            .filter((d) => String(d.id) !== el.value)
                            .map((d) => ({
                                text: d.matchedSynonym
                                    ? `${d.text} — matches synonym ${d.matchedSynonym}`
                                    : d.text,
                                value: String(d.id),
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
        settings: {
            hideSelected: true,
        },
        events: {
            search: debounceSearch(onSearch),
            afterChange: (newVal) => {
                // This is kind of a hack to get x-form-state for the form to set the
                // dirty state when the value changes
                el.form?.dispatchEvent(new CustomEvent("form-state.dirty"))
            },
        },
    })

    cleanup(() => {
        select.destroy()
    })
}

export default TaxonField
