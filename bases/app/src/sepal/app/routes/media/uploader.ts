import Uppy from "@uppy/core"
import AwsS3 from "@uppy/aws-s3"
import Dashboard from "@uppy/dashboard"
import htmx from "htmx.org"

export default (el, directive, { cleanup, evaluate }) => {
    const {
        trigger,
        antiForgeryToken,
        signingUrl,
        uploadedUrl,
        linkResourceType,
        linkResourceId,
    } = evaluate(directive.expression)
    const link = linkResourceType ? { linkResourceType, linkResourceId } : {}
    const uppy = new Uppy()
        .use(Dashboard, {
            trigger: trigger,
            proudlyDisplayPoweredByUppy: true,
        })
        .use(AwsS3, {
            // The server presigns a single PUT per file, so there are no
            // multipart requests to sign.
            shouldUseMultipart: false,
            // signRequest is passed only the key, so the key is the file id and
            // the server answers with the key it chose.
            generateObjectKey: (file) => file.id,
            async signRequest({ method, key }) {
                if (method !== "PUT") {
                    throw new Error(`Cannot sign a ${method} request`)
                }
                const file = uppy.getFile(key)
                const response = await fetch(signingUrl, {
                    method: "POST",
                    headers: { "X-CSRF-Token": antiForgeryToken },
                    body: new URLSearchParams({
                        filename: file.name ?? "",
                        contentType: file.type || "application/octet-stream",
                    }),
                })
                if (!response.ok) {
                    throw new Error("Could not sign the upload")
                }
                return response.json()
            },
        })
        .on("upload-success", (file, response) => {
            htmx.ajax("POST", uploadedUrl, {
                values: { s3Key: response.body?.key, filename: file?.name, ...link },
                target: "#media-list",
                swap: "afterbegin",
                headers: { "X-CSRF-Token": antiForgeryToken },
            })
            // The empty page's first upload: the new tile lands in the list,
            // so the empty state has outlived its use until the next reload.
            document.getElementById("media-empty")?.remove()
        })

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
