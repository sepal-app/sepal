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
                    // An expired session redirects to the login page, which
                    // is not a signature.
                    redirect: "error",
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

    const recordParams = (s3Key: string, filename: string) => {
        const params = new URLSearchParams({ s3Key, filename })
        // Sent from a record's Media tab, to link the upload to the record.
        if (linkResourceType) {
            params.set("linkResourceType", linkResourceType)
            params.set("linkResourceId", String(linkResourceId))
        }
        return params
    }

    // Record each uploaded file as a media item and add its tile to the list.
    // One at a time, so the tiles land in order and a failure marks only its
    // own file.
    uppy.addPostProcessor(async (fileIDs) => {
        for (const id of fileIDs) {
            const file = uppy.getFile(id)
            const key = file?.response?.body?.key
            if (!file || file.error || !key) continue

            uppy.emit("postprocess-progress", file, {
                mode: "indeterminate",
                message: "Saving",
            })
            try {
                const response = await fetch(uploadedUrl, {
                    method: "POST",
                    headers: { "X-CSRF-Token": antiForgeryToken },
                    redirect: "error",
                    body: recordParams(key, file.name ?? ""),
                })
                if (!response.ok) {
                    throw new Error("Could not save the upload")
                }
                htmx.swap("#media-list", await response.text(), {
                    swapStyle: "afterbegin",
                })
                // The empty page's first upload: the new tile is in the list,
                // so the empty state has outlived its use until the next reload.
                document.getElementById("media-empty")?.remove()
            } catch (error) {
                uppy.emit("upload-error", file, error as Error)
            } finally {
                uppy.emit("postprocess-complete", file)
            }
        }
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
