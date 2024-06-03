import Uppy from "@uppy/core"
import Dashboard from "@uppy/dashboard"
import { createApp } from "vue"
import MediaUploader from "./media/MediaUploader.vue"
import "flowbite"

const app = createApp({}).component("media-uploader", MediaUploader).mount("#media-page")
