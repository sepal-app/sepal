import Uppy from "@uppy/core"
import AwsS3 from "@uppy/aws-s3"
import Dashboard from "@uppy/dashboard"
import htmx from "htmx.org"

export default (el, directive, { cleanup, evaluate }) => {
    const { trigger, antiForgeryToken, signingUrl, organizationId } = evaluate(
        directive.expression,
    )
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
            async getUploadParameters(file, options) {
                const formId = file.id.replace(/\//g, "_")
                const form = document.querySelector(
                    `#upload-success-forms form#${formId}`,
                )
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
        .on("upload-success", (file, response) => {
            const formId = file.id.replace(/\//g, "_")
            // trigger form that will post to /media/uploaded
            htmx.trigger(`form#${formId}`, "submit").then("DONE")
        })

    uppy.addPreProcessor(async (fileIds) => {
        const files = fileIds.map((id) => {
            const f = uppy.getFile(id)
            return { filename: f.name, contentType: f.type, size: f.size, id: id }
        })

        // This will populate #upload-success-forms with forms that including
        // inputs with the signing and when submitted will create the media
        // items in the database
        await htmx.ajax("POST", signingUrl, {
            values: { files, organizationId },
            target: "#upload-success-forms",
            headers: {
                "X-CSRF-Token": antiForgeryToken,
            },
        })
    })
}
