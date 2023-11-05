// vite.config.js
import { fileURLToPath, URL } from "node:url"

import { defineConfig } from "vite"
import vue from "@vitejs/plugin-vue"
import { viteCommonjs } from "@originjs/vite-plugin-commonjs"

export default defineConfig({
    plugins: [
        vue(), // viteCommonjs()
    ],
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
        // commonjsOptions: {
        //     transformMixedEsModules: true,
        // },
        // generate manifest.json in outDir
        manifest: true,
        // assetsDir: "",
        rollupOptions: {
            output: {
                dir: "resources/app/dist",
                // assetFileNames: "[name]-[hash][extname]",
            },
            input: [
                "resources/app/static/js/shared.js",
                "resources/app/static/js/taxon_form.ts",
                // "resources/app/static/js/taxon_form.vue",
                "resources/app/static/css/main.css",
            ],
        },
    },
})
