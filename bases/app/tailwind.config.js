/** @type {import('tailwindcss').Config} */
const defaultTheme = require("tailwindcss/defaultTheme")

module.exports = {
    content: {
        files: [
            "./src/**/*.{html,js,clj,css,vue}",
            "./resources/**/*.{html,js,clj,css,vue}",
            "./node_modules/flowbite/**/*.js",
        ],
    },
    theme: {
        extend: {
            fontFamily: {
                sans: ["Inter var", ...defaultTheme.fontFamily.sans],
            },
        },
    },
    plugins: [
        require("@tailwindcss/typography"),
        require("@tailwindcss/forms"),
        require("@tailwindcss/aspect-ratio"),
        require("flowbite/plugin"),
    ],
}
