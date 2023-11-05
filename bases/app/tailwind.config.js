/** @type {import('tailwindcss').Config} */
module.exports = {
    content: {
        files: [
            "./src/**/*.{html,js,clj,css,vue}",
            "./resources/**/*.{html,js,clj,css,vue}",
        ],
    },
    theme: {
        extend: {},
    },
    plugins: [
        require("@tailwindcss/typography"),
        require("@tailwindcss/forms"),
        require("@tailwindcss/aspect-ratio"),
    ],
}
