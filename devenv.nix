{ pkgs, lib, config, ... }:
{
  dotenv = {
    enable = true;
    filename = ".env.local";
  };

  packages = with pkgs; [
    babashka
    bbin
    clj-kondo
    cljfmt
    clojure-lsp
    google-cloud-sdk
    libspatialite
    sqlfluff
    sqlite
  ] ++ lib.optionals pkgs.stdenv.isLinux [ pkgs.glibcLocales ];

  languages.clojure.enable = true;
  languages.javascript = {
    enable = true;
    package = pkgs.nodejs_22;
  };

  # Anything whose value has to be an absolute path lives here rather than in
  # .env.local, because devenv's dotenv reader does not shell-expand — a
  # "${PWD}/db/migrations" in .env.local would arrive literally. Keeping these
  # here also means they follow the project directory instead of breaking when
  # it moves. Everything else, including all secrets, stays in .env.local.
  env = {
    EXTENSIONS_LIBRARY_PATH = "${pkgs.libspatialite}/lib";
    MIGRATIONS_DIR = "${config.devenv.root}/db/migrations";
    SCHEMA_DUMP_FILE = "${config.devenv.root}/db/schema.sql";
    SEPAL_DATA_HOME = "${config.devenv.root}/.local";
    VITE_CONFIG_FILE = "${config.devenv.root}/bases/app/vite.config.dev.js";
  };
}
