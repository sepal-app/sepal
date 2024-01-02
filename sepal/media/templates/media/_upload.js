import Uppy from "@uppy/core"
import Dashboard from "@uppy/dashboard"
import XHRUpload from "@uppy/xhr-upload"

/*
 * This file is a jinja template so that we can inject the CSRF token for the
 * media upload form.
 */

Alpine.store("showMediaListEmptyState", true)

const uppy = new Uppy({
    logger: {
        debug: (...args) => console.log("DEBUG: ", ...args),
        warn: (...args) => console.log("WARN: ", ...args),
        error: (...args) => console.log("ERROR: ", ...args),
    },
}).use(Dashboard, {
    trigger: "#upload-button",
})

uppy.use(XHRUpload, {
    endpoint: "{{ upload_endpoint }}",
    allowedMetaFields: ["key", "AWSAccessKeyId", "policy", "signature", "Content-Type"],
    getResponseData: (responseText, response) => {
        // overide so uppy doesn't try to json parse the response
        return {}
    },
})

uppy.on("file-added", async (file) => {
    // This will inject a form into the form-list for each file. After the
    // upload is successfull we'll submit the form to create the media entity in
    // the database. We're doing this manually instead since using htmx.ajax()
    // seemed to have an issue with the inserted html not being "settled" when
    // we try to get the form element for the file meta values.
    const fd = new FormData()

    fd.append("csrf_token", "{{ create_upload_form.csrf_token._value() }}")
    fd.append("id", file.id)
    fd.append("name", file.name)
    fd.append("extension", file.extension)
    fd.append("size", file.size)
    fd.append("media_type", file.type)

    const r = await fetch("{{ url_for('media.create_upload_form') }}", {
        method: "POST",
        body: fd,
    })
    // append the response into the #form-list
    const html = await r.text()
    const formList = document.getElementById("form-list")
    formList.insertAdjacentHTML("beforeend", html)
    htmx.process(formList)

    // TODO: we can still get null forms here.  Maybe we should just listen to the form settle events
    // and all of the file meta for all forms after settle

    // The file meta for the form we just inserted into the DOM
    let form = document.getElementById(file.id)
    // console.log(form)
    const fd2 = new FormData(form)
    uppy.setFileMeta(file.id, {
        key: fd2.get("key"),
        AWSAccessKeyId: fd2.get("AWSAccessKeyId"),
        policy: fd2.get("policy"),
        signature: fd2.get("signature"),
        "Content-Type": fd2.get("Content-Type"),
    })
})

uppy.on("upload-success", (file, response) => {
    const selector = `#${file.id.replaceAll("/", "\\/")}`
    htmx.trigger(selector, "submit", {})
    document.getElementById(file.id)?.remove()
})

uppy.on("file-removed", (file, reason) => {
    document.getElementById(file.id)?.remove()
})

uppy.on("complete", (result) => {
    console.log("Upload complete! We’ve uploaded these files:", result.successful)
    Alpine.store("showMediaListEmptyState", false)
})
