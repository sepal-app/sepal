{ pkgs, lib, config, ... }:
let
  # The migration runner bin/reset-db.sh shells out to. It lives in its own
  # repo rather than nixpkgs, so it is pinned here by revision: without it
  # reset-db.sh dies on `migrate.sh: command not found`, and nothing else in
  # the shell supplies it.
  sqlite-migrate = pkgs.stdenv.mkDerivation {
    pname = "sqlite-migrate";
    version = "0-unstable-2026-09-10";
    src = pkgs.fetchFromGitHub {
      owner = "brettatoms";
      repo = "sqlite-migrate";
      rev = "5ea609c5976e424c032692457986675d49b170c5";
      hash = "sha256-lUUyjPgoqmNqGjPxqc/Y75ZrqXEkWyDFf2G3L5u9ao4=";
    };
    dontBuild = true;
    installPhase = "install -Dm755 migrate.sh $out/bin/migrate.sh";
  };
in
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
  ] ++ [ sqlite-migrate ]
  ++ lib.optionals pkgs.stdenv.isLinux [ pkgs.glibcLocales ];

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
