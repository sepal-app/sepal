import TomSelect from "tom-select"

export default (el, directive, { cleanup, evaluate }) => {
    const { url, exclude } = evaluate(directive.expression)

    function load(query, callback) {
        const params = new URLSearchParams({ q: query, page_size: 6 })
        fetch(url + "?" + params.toString(), {
            headers: { Accept: "application/json" },
        })
            .then((response) => response.json())
            .then((data) => {
                console.log(data)
                return data
            })
            .then(callback)
            .catch((e) => {
                console.error(e)
                callback()
            })
    }

    const tomselect = new TomSelect(el, {
        itemClass: "sm:text-sm bg-white",
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
