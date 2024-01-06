import { fileURLToPath, URL } from "node:url"

import { defineConfig } from "vite"
import vue from "@vitejs/plugin-vue"

export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            "@": fileURLToPath(new URL("./src", import.meta.url)),
            vue: "vue/dist/vue.esm-bundler.js", // This was needed.
        },
    },
    optimizeDeps: {
        include: ["linked-dep"],
    },
    build: {
        manifest: true,
        rollupOptions: {
            output: {
                dir: "resources/app/dist",
            },
            input: [
                "resources/app/static/js/alpine.js",
                "resources/app/static/js/htmx.js",
                "resources/app/static/js/accession_form.ts",
                "resources/app/static/js/material_form.ts",
                "resources/app/static/js/taxon_form.ts",
                "resources/app/static/js/media.ts",
                "resources/app/static/css/main.css",
                "resources/app/static/img/auth/jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg",
            ],
        },
    },
})
