/** @type {import('tailwindcss').Config} */
const defaultTheme = require("tailwindcss/defaultTheme")

module.exports = {
    content: {
        files: [
            "./src/**/*.{html,js,ts,clj,css}",
            "./resources/**/*.{html,js,ts,clj,css}",
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
        // require("@tailwindcss/typography"),
        // Tailwind forms conflicts with daisy
        require("@tailwindcss/forms"),
        // require("@tailwindcss/aspect-ratio"),
        require("daisyui"),
    ],
    daisyui: {
        themes: [
            {
                light: {
                    ...require("daisyui/src/theming/themes")["emerald"],
                },
            },
        ],
        base: true, // applies background color and foreground color for root element by default
        styled: true, // include daisyUI colors and design decisions for all components
        utils: true, // adds responsive and modifier utility classes
        prefix: "", // prefix for daisyUI classnames (components, modifiers and responsive class names. Not colors)
        logs: true, // Shows info about daisyUI version and used config in the console when building your CSS
        themeRoot: ":root", // The element that receives theme color CSS variables
    },
}
