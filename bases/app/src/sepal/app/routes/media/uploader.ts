import Uppy from "@uppy/core"
import AwsS3 from "@uppy/aws-s3"
import Dashboard from "@uppy/dashboard"
import htmx from "htmx.org"

export default (el, directive, { cleanup, evaluate }) => {
    const { trigger, antiForgeryToken, signingUrl, linkResourceType, linkResourceId } =
        evaluate(directive.expression)
    const uppy = new Uppy({
        logger: {
            // debug: (...args) => console.log("DEBUG: ", ...args),
            debug: (...args) => {},
            warn: (...args) => console.log("WARN: ", ...args),
            error: (...args) => console.log("ERROR: ", ...args),
        },
    })
        .use(Dashboard, {
            trigger: trigger,
            showProgressDetails: true,
            proudlyDisplayPoweredByUppy: true,
        })
        .use(AwsS3, {
            async getUploadParameters(file) {
                const formId = CSS.escape(file.id.replace(/\//g, "_"))
                const form = document.querySelector(
                    `#upload-success-forms form#${formId}`,
                )
                if (!form) {
                    throw `ERROR: Could not find form: ${formId}`
                }

                const values = htmx.values(form)
                return {
                    method: values.s3Method,
                    url: values.s3Url,
                    fields: {}, // For presigned PUT uploads, this should be left empty.
                    headers: {
                        "content-type": values.contentType,
                    },
                }
            },
        })
        .on("upload-success", (file) => {
            const formId = file?.id.replace(/\//g, "_")
            // trigger form that will post to /media/uploaded
            htmx.trigger(`form#${formId}`, "submit", {})
            // The empty page's first upload: the new tile lands in the list,
            // so the empty state has outlived its use until the next reload.
            document.getElementById("media-empty")?.remove()
        })

    uppy.addPreProcessor(async (fileIds) => {
        const files = fileIds.map((id) => {
            const f = uppy.getFile(id)
            // Stringify each file object - backend expects JSON strings
            return JSON.stringify({
                filename: f.name,
                contentType: f.type,
                size: f.size,
                id: id,
            })
        })

        // avoid sending "undefined" for linkResourceType and linkResourceId
        const values = linkResourceType
            ? { files, linkResourceType, linkResourceId }
            : { files }

        // This will populate #upload-success-forms with forms that including
        // inputs with the signing and when submitted will create the media
        // items in the database
        await htmx.ajax("POST", signingUrl, {
            values,
            target: "#upload-success-forms",
            swap: "beforeend",
            headers: {
                "X-CSRF-Token": antiForgeryToken,
            },
        })
    })

    // The empty page's "Upload" button opens the same dashboard the title-bar
    // button does; Uppy's Dashboard trigger takes one element, so the second
    // trigger is bound by hand.
    document
        .getElementById("media-empty-upload")
        ?.addEventListener("click", () => uppy.getPlugin("Dashboard").openModal())

    // The empty state is also a drop target. Uppy's own dashboard has one, but
    // dropping straight onto the page's empty state should behave the same way.
    const dropTarget = document.querySelector(
        "[data-media-drop-target]",
    ) as HTMLElement | null
    if (dropTarget) {
        const addFiles = (files: FileList) =>
            uppy.addFiles(
                Array.from(files).map((f) => ({
                    name: f.name,
                    type: f.type,
                    size: f.size,
                    data: f,
                })),
            )
        const onDragOver = (e: DragEvent) => {
            e.preventDefault()
            dropTarget.classList.add("spl-drop-active")
        }
        const onDragLeave = () => dropTarget.classList.remove("spl-drop-active")
        const onDrop = (e: DragEvent) => {
            e.preventDefault()
            dropTarget.classList.remove("spl-drop-active")
            if (e.dataTransfer?.files?.length) {
                addFiles(e.dataTransfer.files)
                uppy.getPlugin("Dashboard").openModal()
            }
        }
        dropTarget.addEventListener("dragover", onDragOver)
        dropTarget.addEventListener("dragleave", onDragLeave)
        dropTarget.addEventListener("drop", onDrop)
        cleanup(() => {
            dropTarget.removeEventListener("dragover", onDragOver)
            dropTarget.removeEventListener("dragleave", onDragLeave)
            dropTarget.removeEventListener("drop", onDrop)
        })
    }
}
