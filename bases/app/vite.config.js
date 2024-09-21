import { fileURLToPath, URL } from "node:url"

import { defineConfig } from "vite"

export default defineConfig({
    plugins: [],
    resolve: {
        alias: {
            "@": fileURLToPath(new URL("./src", import.meta.url)),
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
                "resources/app/static/css/main.css",
                "resources/app/static/css/media.css",
                "resources/app/static/img/auth/jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg",
                "resources/app/static/js/accession_form.ts",
                "resources/app/static/js/location_form.ts",
                "resources/app/static/js/material_form.ts",
                "resources/app/static/js/media.ts",
                "resources/app/static/js/media_detail.ts",
                "resources/app/static/js/page.ts",
                "resources/app/static/js/taxon_form.ts",
                "resources/app/static/js/auth/page.ts",
            ],
        },
    },
})
