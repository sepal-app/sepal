/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{html,js,clj}", "./resources/**/*.{html,js,clj}"],
  theme: {
    extend: {},
  },
  plugins: [
    require("@tailwindcss/typography"),
    require("@tailwindcss/forms"),
    require("@tailwindcss/aspect-ratio"),
  ],
}
