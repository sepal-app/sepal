import path from "path"
import defaultTheme from "tailwindcss/defaultTheme"
import forms from "@tailwindcss/forms"
import daisyui from "daisyui"
import themes from "daisyui/src/theming/themes"

// TODO: Move this config to CSS

module.exports = {
    content: {
        files: [path.resolve(__dirname, "src/**/*.{html,js,ts,clj,css}")],
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
        forms,
        // require("@tailwindcss/aspect-ratio"),
        daisyui,
    ],
    daisyui: {
        themes: [
            {
                light: {
                    ...themes["emerald"],
                    "base-100": "#f7f8fc",
                    ".input": {
                        "background-color": "white",
                    },
                    ".textarea": {
                        "background-color": "white",
                    },
                    ".select": {
                        "background-color": "white",
                    },
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
