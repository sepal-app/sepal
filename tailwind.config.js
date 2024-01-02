/** @type {import('tailwindcss').Config} */
module.exports = {
    content: ["./sepal/**/*.{html,js}"],
    theme: {
        extend: {},
    },
    plugins: [
        require("@tailwindcss/typography"),
        require("@tailwindcss/forms"),
        require("@tailwindcss/line-clamp"),
        require("@tailwindcss/aspect-ratio"),
    ],
}
