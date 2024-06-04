import Alpine from "alpinejs"
import MediaUploader from "./media/MediaUploader.ts"

document.addEventListener("alpine:init", () => {
    Alpine.directive("media-uploader", MediaUploader)
})
