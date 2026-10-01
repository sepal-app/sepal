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
    # xgettext, msgmerge and msgfmt, for bin/i18n-extract and bin/i18n-check.
    gettext
    google-cloud-sdk
    libspatialite
    # cwebp, for sepal.app.screenshot. Playwright writes PNG only, and the
    # marketing site wants WebP.
    libwebp
    sqlfluff
    sqlite
  ] ++ lib.optionals pkgs.stdenv.hostPlatform.isLinux [ pkgs.glibcLocales ];

  languages.clojure.enable = true;
  # sepal.app.instance derives per-instance secrets with javax.crypto.KDF,
  # finalized in JDK 25. The default JDK here is 21, which cannot load it.
  languages.java.jdk.package = pkgs.jdk25;
  languages.javascript = {
    enable = true;
    package = pkgs.nodejs_22;
  };

  # Anything whose value has to be an absolute path lives here rather than in
  # .env.local, because devenv's dotenv reader does not shell-expand — a
  # "${PWD}/.local" in .env.local would arrive literally. Keeping these here
  # also means they follow the project directory instead of breaking when it
  # moves. Everything else, including all secrets, stays in .env.local.
  env = {
    EXTENSIONS_LIBRARY_PATH = "${pkgs.libspatialite}/lib";
    SEPAL_DATA_HOME = "${config.devenv.root}/.local";
  };
}
