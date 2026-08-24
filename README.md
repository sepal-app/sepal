# Sepal

A botanical collection management system for managing plant accessions,
materials, locations, and taxonomic data. Integrates with the [World Flora
Online](https://www.worldfloraonline.org/) Plantlist for taxonomy reference.

## Features

- Plant accession tracking
- Material management
- Location management
- Taxonomic data with WFO Plantlist integration
- Media attachments
- Full-text search

## Running Sepal

Sepal is one process backed by one SQLite database. It provisions that database
on first start, applies pending migrations, and walks you through a setup wizard
that creates the first admin user and downloads the taxon data. `SEPAL_SECRET` is
the only variable you have to set.

### Docker

`projects/app/Dockerfile` builds the whole thing — frontend assets, uberjar and a
runtime image carrying SpatiaLite.

```bash
docker build -f projects/app/Dockerfile -t sepal .
docker run -d -p 3000:3000 \
    -e SEPAL_SECRET="$(openssl rand -hex 16)" \
    -v sepal-data:/root/.local/share/Sepal \
    sepal
```

Then open `http://localhost:3000` and follow the setup wizard.

`bin/smoke-test` does exactly this against an empty volume and asserts the
container provisions, migrates, loads SpatiaLite and serves HTTP, so it is the
executable version of the paragraph above.

### From a jar

```bash
bin/build-uberjar.sh
SEPAL_SECRET="$(openssl rand -hex 16)" \
    java -Duser.timezone=UTC --enable-native-access=ALL-UNNAMED \
         -jar projects/app/target/sepal.jar
```

Outside Docker you also need `mod_spatialite` on disk and
`EXTENSIONS_LIBRARY_PATH` pointing at the directory holding it.

### Where data lives

Everything Sepal writes — the database, the thumbnail cache, backups — goes under
`SEPAL_DATA_HOME`. Unset, it falls back to `$XDG_DATA_HOME/Sepal`, then to
`~/Library/Application Support/Sepal` on macOS or `~/.local/share/Sepal`
elsewhere.

## Configuration

`sepal.app.main/env-opts` is the only place Sepal reads the environment.
Everything it reads is listed here; anything not listed is not read.

### Required

| Variable | Description |
|----------|-------------|
| `SEPAL_SECRET` | Master secret, minimum 16 characters. The session cookie key and the password reset token secret are both HKDF-derived from it, so it has no default and changing it invalidates every session. |

### Server

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_DOMAIN` | `localhost` | Domain used to build links in outgoing email |
| `HOST` | `0.0.0.0` | Jetty bind address |
| `PORT` | `3000` | Jetty port |
| `LOG_LEVEL` | `DEBUG` | `DEBUG`, `INFO`, `WARN` or `ERROR` |
| `SEPAL_DATA_HOME` | platform default | Directory for the database, cache and backups |
| `BACKUP_PATH` | `$SEPAL_DATA_HOME/backups` | Where nightly backups are written |
| `EXTENSIONS_LIBRARY_PATH` | — | Directory containing `mod_spatialite` |

### Email

Optional. Without `SMTP_HOST` no mail client is built at all, and password
resets and invitations go nowhere.

| Variable | Default | Description |
|----------|---------|-------------|
| `SMTP_HOST` | — | SMTP hostname. Setting it is what turns email on |
| `SMTP_PORT` | `587` | 587 for STARTTLS, 465 for SSL |
| `SMTP_USERNAME` | — | SMTP auth username |
| `SMTP_PASSWORD` | — | SMTP auth password |
| `SMTP_AUTH` | `true` | Whether to authenticate |
| `SMTP_TLS` | `starttls` | `starttls`, `ssl` or `none` |
| `SMTP_DEBUG` | off | Log the SMTP conversation to stdout. Prints every address and every server reply |
| `FORGOT_PASSWORD_EMAIL_FROM` | `support@sepal.app` | Sender for password reset emails |
| `FORGOT_PASSWORD_EMAIL_SUBJECT` | `Sepal - Reset Password` | Subject for password reset emails |
| `INVITATION_EMAIL_FROM` | `noreply@sepal.app` | Sender for invitation emails |
| `INVITATION_EMAIL_SUBJECT` | `You've been invited to Sepal` | Subject for invitation emails |

### Media uploads

Optional. Any S3-compatible store works, Cloudflare R2 included. Without
`AWS_ACCESS_KEY_ID` no S3 client is built and media upload is off.

| Variable | Default | Description |
|----------|---------|-------------|
| `AWS_ACCESS_KEY_ID` | — | Access key. Setting it is what turns media upload on |
| `AWS_SECRET_ACCESS_KEY` | — | Secret key |
| `AWS_S3_ENDPOINT` | — | Endpoint origin, e.g. `https://<accountid>.r2.cloudflarestorage.com`. No bucket path: the bucket is appended |
| `AWS_REGION` | — | Region used to sign requests. With R2, `auto` |
| `MEDIA_UPLOAD_BUCKET` | `media` | Bucket for media uploads |
| `MEDIA_KEY_PREFIX` | `media/` | Prefix every media key is stored under. Must end in a slash |
| `IMAGE_CACHE_SIZE_MB` | `500` | Thumbnail cache ceiling, cached under `$SEPAL_DATA_HOME/cache` |

With Cloudflare R2 specifically:

- Use an API token scoped to the one bucket.
- Set `AWS_REGION=auto`. The region only signs requests; R2 accepts `auto` for
  all buckets.
- The browser uploads directly to the bucket with a presigned PUT, so the bucket
  needs a CORS rule allowing `PUT` from your app's origin with the
  `content-type` header. Reads need no CORS rule: the app proxies them.

## Development

Dependencies come from [devenv](https://devenv.sh), entered by
[direnv](https://direnv.net). Everything below assumes you are in that shell.

```bash
direnv allow          # once
devenv shell          # or just cd, with direnv active
```

Copy `.env.local.example` to `.env.local` and set `SEPAL_SECRET`. Note that
devenv's dotenv reader is not a shell: it keeps quotes as part of the value and
does not expand variables, so write values unquoted and paths in full. The
variables that must be absolute paths — `EXTENSIONS_LIBRARY_PATH` and
`SEPAL_DATA_HOME` — are set by `devenv.nix` and should not be set in
`.env.local`.

Start a REPL with the `dev` alias, then:

```clojure
(go)         ; start the system
(stop)       ; stop it
(restart)    ; both
```

`(go)` builds its options with `sepal.app.main/env-opts` from the real
environment, exactly as the self-hosted entry point does, then adds the vite dev
server, hot reload and per-request reload. It provisions and migrates the
database if needed, so a fresh checkout needs no database setup step.

### Tests and linting

```bash
clojure -M:dev:test:test-runner            # unit tests
clojure -M:dev:test:test-e2e:test-runner :e2e   # e2e, needs Playwright
bin/lint                                   # clj-kondo and cljfmt check
clojure -M:cljfmt fix                      # format
clojure -M:poly check                      # Polylith checks
```

### Frontend assets

Run from `bases/app/`. Changes to TypeScript require a rebuild; changes to
Clojure are hot-reloaded in the REPL.

```bash
cd bases/app
npm run build    # production build
npm run dev      # dev server with HMR
```

### Loading taxa from a full WFO database

The setup wizard downloads a prebuilt taxon database, which is the right path
for almost everything. `bin/reset-db.sh` exists for the case where you want to
rebuild a development database from the full WFO Plantlist instead: it drops the
database, loads the schema, applies migrations and inserts every taxon.

It needs the WFO SQLite database and
[sqlite-migrate](https://github.com/brettatoms/sqlite-migrate):

- Download a prebuilt database: [10.5281/zenodo.17444674](https://doi.org/10.5281/zenodo.17444674)
- Or build one: [wfo-plantlist-sqlite](https://github.com/brettatoms/wfo-plantlist-sqlite)

```bash
bin/reset-db.sh
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=secret bin/reset-db.sh   # and an admin user
```

`bin/create-init-db.sh` builds the compact init database the setup wizard
downloads, and `bin/publish-sepal-init.sh` publishes it as a GitHub release.

### User management

Registration is open only through the setup wizard and invitations. The roles
are `admin` (organization settings, user management, all CRUD), `editor`
(create, edit and delete plant records, edit own profile) and `reader` (view
plant records, edit own profile).

There is also a CLI, useful when email is not configured:

```bash
clojure -M:dev:cli create-user --email admin@example.com --password secret --role admin
clojure -M:dev:cli list-users
clojure -M:dev:cli routes
```

### Troubleshooting SQLite extensions

If loading SpatiaLite fails, set `EXTENSIONS_LIBRARY_PATH` to the directory
holding `mod_spatialite`. In the devenv shell this is already set to
`${pkgs.libspatialite}/lib` and should need no attention.

## Contributing

Contributions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening
a pull request — it covers the development workflow and the [Contributor License
Agreement](CLA.md), which first-time contributors need to sign.

Architecture notes for the codebase live in [AGENTS.md](AGENTS.md).

## License

Sepal is free software, licensed under the [GNU Affero General Public License
version 3.0](LICENSE). You can run it, read it, modify it, and host it yourself.
If you host a modified version as a network service, the AGPL requires you to
make your modifications available to its users.

Copyright © Sepal LLC.

Sepal LLC also offers Sepal under commercial license terms for organizations that
cannot use AGPL-licensed software. Contact brett@sepal.app.
