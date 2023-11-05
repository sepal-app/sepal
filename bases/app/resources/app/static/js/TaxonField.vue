<script setup>
import { onMounted, ref } from "vue"
import TomSelect from "tom-select"

const props = defineProps(["url", "taxonId", "initialValue"])
const select = ref()

function renderParent(item, escape) {
    console.log(item)
    return item.text
        ? `<div>${escape(item.text)}</div>`
        : `<div>${escape(item.name)} ${escape(item.author ?? "")}</div>`
}

onMounted(() => {
    const options = props.initialValue ? [JSON.parse(props.initialValue)] : []

    this._ts = new TomSelect(select.value, {
        itemClass: "sm:text-sm bg-white",
        maxItems: 1,
        openOnFocus: false,
        optionClass: "sm:text-sm bg-white py-2 px-3",
        searchField: "name",
        selectOnTab: true,
        valueField: "id",
        options: options,
        items: options.length ? [options[0].id] : [],
        load: (query, callback) => {
            const params = new URLSearchParams({ q: query, page_size: 6 })
            fetch(props.url + "?" + params.toString(), {
                headers: { Accept: "application/json" },
            })
                .then((response) => response.json())
                // remove the current taxon from the complete list
                .then((data) => {
                    console.log("---")
                    console.log(data)
                    console.log(data[0].id.toString())
                    console.log(props.taxonId)
                    const d = data.filter((t) => t.id.toString() !== props.taxonId)
                    console.log("filtered: ", d)
                    return d
                })
                .then(callback)
                .catch((e) => {
                    console.error(e)
                    callback()
                })
        },
        render: {
            option: renderParent,
            item: renderParent,
        },
    })
})
</script>

<template>
    <select ref="select">
        <slot></slot>
    </select>
</template>
