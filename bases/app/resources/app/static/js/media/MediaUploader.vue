<script setup>
import Uppy from "@uppy/core"
import AwsS3 from "@uppy/aws-s3"
import Dashboard from "@uppy/dashboard"
import { onMounted, ref } from "vue"

const props = defineProps([
    "signing-url",
    "anti-forgery-token",
    "trigger",
    "organization-id",
])
const form = ref()

onMounted(() => {
    new Uppy({
        logger: {
            debug: (...args) => console.log("DEBUG: ", ...args),
            warn: (...args) => console.log("WARN: ", ...args),
            error: (...args) => console.log("ERROR: ", ...args),
        },
    })
        .use(Dashboard, {
            trigger: props.trigger,
            showProgressDetails: true,
            proudlyDisplayPoweredByUppy: true,
        })
        .use(AwsS3, {
            async getUploadParameters(file, options) {
                const response = await fetch(props.signingUrl, {
                    method: "POST",
                    headers: {
                        accept: "application/json",
                        "X-CSRF-Token": props.antiForgeryToken,
                    },
                    body: new URLSearchParams({
                        filename: file.name,
                        contentType: file.type,
                        organizationId: props.organizationId,
                    }),
                    signal: options?.signal,
                })

                if (!response.ok)
                    throw new Error("Unsuccessful request", { cause: response })

                // Parse the JSON response.
                const data = await response.json()

                // Return an object in the correct shape.
                return {
                    method: data.method,
                    url: data.url,
                    fields: {}, // For presigned PUT uploads, this should be left empty.
                    // Provide content type header required by S3
                    headers: {
                        "Content-Type": file.type,
                    },
                }
            },
        })
})
</script>
