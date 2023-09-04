const path = require("path");

module.exports = (ctx) => ({
  plugins: [
    require("postcss-import"),
    require("tailwindcss")(path.resolve(__dirname, "tailwind.config.js")),
    require("cssnano")({
      preset: ["default", { discardComments: { removeAll: true } }],
    }),
    require("autoprefixer"),
  ],
});
