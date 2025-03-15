import { defineConfig } from "vite"
import tailwindcss from "@tailwindcss/vite"

export default defineConfig({
    root: __dirname.concat("/src/sepal"),
    mode: "production",
    resolve: {
        alias: {
            "~": __dirname.concat("/src/sepal/app"),
        },
    },
    plugins: [tailwindcss()],
    build: {
        manifest: true,
        outDir: __dirname.concat("/resources/app/build"),
        emptyOutDir: true,
        rollupOptions: {
            input: [
                "~/css/main.css",
                "~/routes/accession/form.ts",
                "~/routes/auth/img/jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg",
                "~/routes/auth/page.ts",
                "~/routes/material/form.ts",
                "~/routes/media/css/media.css",
                "~/routes/media/media.ts",
                "~/routes/media/detail.ts",
                "~/routes/taxon/form.ts",
                "~/ui/page.ts",
            ],
        },
    },
})
