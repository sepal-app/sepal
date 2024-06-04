import Uppy from "@uppy/core"
import AwsS3 from "@uppy/aws-s3"
import Dashboard from "@uppy/dashboard"
import htmx from "htmx.org"

export default (el, directive, { cleanup, evaluate }) => {
    const { trigger, antiForgeryToken, signingUrl, organizationId } = evaluate(
        directive.expression,
    )
    new Uppy({
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
                // The signing url returns an html form that gets prepended the
                // the children of #upload-success-forms. We then get the upload
                // parameters from the input values of the form. On
                // 'upload-success' we submit these to create the media records
                // in the database.
                await htmx.ajax("POST", signingUrl, {
                    target: "#upload-success-forms",
                    swap: "afterbegin",
                    headers: {
                        "X-CSRF-Token": antiForgeryToken,
                    },
                    values: {
                        filename: file.name,
                        contentType: file.type,
                        size: file.size,
                        fileId: file.id,
                        organizationId: organizationId,
                    },
                })
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
            htmx.trigger(`form#${formId}`, "submit")
        })
}
