import path from "path"
import importPlugin from "postcss-import"
import tailwindcss from "tailwindcss"
import cssnano from "cssnano"
import autoprefixer from "autoprefixer"
import { fileURLToPath } from "url"

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export default (_ctx) => ({
    plugins: [
        importPlugin,
        tailwindcss(path.resolve(__dirname, "tailwind.config.js")),
        cssnano({ preset: ["default", { discardComments: { removeAll: true } }] }),
        autoprefixer,
    ],
})
