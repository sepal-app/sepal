import Alpine from "alpinejs"
import MediaUploader from "~/routes/media/uploader"

document.addEventListener("alpine:init", () => {
    Alpine.directive("media-uploader", MediaUploader)
})
