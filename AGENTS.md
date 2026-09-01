# AGENTS.md

Sepal is a botanical collection management system for managing plant accessions, materials, locations, and taxonomic data. It integrates with the World Flora Online (WFO) Plantlist database for taxonomy reference.

## Architecture

This is a **Polylith** monorepo with the following structure:

- `bases/app/` - The main web application (Ring/Reitit server with HTMX frontend)
- `components/` - Domain components with public interfaces
- `development/` - REPL development entry point
- `projects/` - Deployable artifacts
- `components/database/resources/database/` - Schema and SQL migrations, read from the classpath

### Bases vs Components

**Components** (`components/`) contain reusable domain logic:
- Each component exposes functionality through `<component>.interface` namespace
- Components are reusable across different bases
- Domain logic: validation schemas, database queries, business rules
- Examples: `validation`, `user`, `accession`, `taxon`

**Bases** (`bases/`) contain application-specific code:
- `bases/app/` - The web application
  - Routes (`routes/<resource>/`)
  - Generic UI components (`ui/`) - reusable elements (forms, tables, buttons); resource-specific UI belongs in routes
  - Frontend assets (TypeScript, CSS in `src/sepal/app/`)
  - App-specific test helpers (`test/sepal/app/test.clj`)

**Key distinctions:**
- Frontend code lives in the base, not components
- Test fixtures for HTTP/app testing belong in the base
- When adding features, ask: "Is this app-specific (base) or reusable domain logic (component)?"

### Component Interface Pattern

Each component follows Polylith conventions:
- Public API in `sepal.<component>.interface` namespace
- Implementation in `sepal.<component>.core` namespace
- Specs/schemas in `sepal.<component>.interface.spec` namespace
- Tests in `sepal.<component>.interface-test` namespace

**Always import from interface namespaces**, not core:
```clojure
;; Good
(:require [sepal.accession.interface :as acc.i])

;; Bad - don't import core directly
(:require [sepal.accession.core :as acc.core])
```

### Interface Return Conventions

Interface functions that retrieve data typically return:
- The entity map when found
- `nil` when not found (NOT an error)
- An error map (checked with `error.i/error?`) only for actual failures (e.g., database errors, validation failures)

Check for `nil` for "not found" cases, not `error.i/error?`:
```clojure
;; Good - check for nil
(if-let [entity (some.i/get-by-id db id)]
  (do-something entity)
  (handle-not-found))

;; Bad - get-by-id returns nil when not found, not an error
(let [entity (some.i/get-by-id db id)]
  (if (error.i/error? entity)  ; This will never be true for "not found"
    (handle-not-found)
    (do-something entity)))
```

Use `error.i/error?` for operations that can fail (create, update, validation):
```clojure
(let [result (some.i/create! db data)]
  (if (error.i/error? result)
    (handle-error result)
    (handle-success result)))
```

## Tech Stack

### Core Libraries
- **Clojure 1.12** - Language
- **Polylith** - Architecture
- **Integrant** - System/component lifecycle
- **Zodiac** - Web framework (wraps Ring/Reitit)
- **Reitit** - Routing
- **Malli** - Schema validation and generation
- **next.jdbc** - Database access
- **HoneySQL** - SQL generation
- **SQLite** - Database (with FTS5 full-text search)
- **Aero** - Configuration with profiles
- **Chassis** - HTML generation (Hiccup-like)
- **HTMX** - Frontend interactivity
- **TailwindCSS** - Styling, with a hand-written `spl-` component layer. No DaisyUI

### Testing
- **Kaocha** - Test runner
- **matcher-combinators** - Assertion library
- **test.check/malli.generator** - Property-based testing
- **Peridot/Kerodon** - HTTP testing

### Tools
- **[sqlite-migrate](https://github.com/brettatoms/sqlite-migrate)** - Database migrations
- **clj-kondo** - Linter
- **cljfmt** - Formatter

## Development

### REPL Startup

The `go`, `stop`, and `restart` functions for managing the system during development are defined in `development/src/user.clj`.

Start REPL with dev alias, then:
```clojure
(go)         ; Start system
(stop)       ; Stop system
(restart)    ; Restart system
```

`(go)` builds its options with `sepal.app.main/env-opts` from the real
environment, exactly as the self-hosted entry point does, then adds the three
development options — the vite dev server, hot reload, and per-request reload.
It needs `SEPAL_SECRET` set in `.env.local` and fails with a message naming the
variable if it is not.

After starting the system with `(go)`, four dynamic vars become available in the `user` namespace for interactive development:
- `*system*`: An Integrant map containing all running components of the application.
- `*db*`: A `next.jdbc` database connection pool for direct database queries.
- `*process*` and `*garden*`: the process and instance values, for `sepal.app.instance` calls.

### Common Commands

```bash
# Run unit tests (default - excludes e2e tests)
clojure -M:dev:test:test-runner

# Run unit tests explicitly (useful if e2e test loading fails)
clojure -M:dev:test:test-runner :unit

# Run unit tests with focus on specific namespace
clojure -M:dev:test:test-runner :unit --focus sepal.accession.interface-test

# Run unit tests with focus on specific test
clojure -M:dev:test:test-runner :unit --focus sepal.accession.interface-test/test-create

# Run e2e tests (requires Playwright - see tests.edn for config)
clojure -M:dev:test:test-e2e:test-runner :e2e

# Lint
bin/lint
# or individually:
clj-kondo --parallel --lint components --lint bases --lint projects
clojure -M:cljfmt check

# Format code
clojure -M:cljfmt fix

# Reset database (loads WFO taxon data)
bin/reset-db.sh

# Reset database and create admin user
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=secret bin/reset-db.sh

# User management CLI
clojure -M:dev:cli create-user --email user@example.com --password secret --role admin
clojure -M:dev:cli create-user --email editor@example.com --password secret --role editor
clojure -M:dev:cli create-user --email reader@example.com --password secret --role reader
clojure -M:dev:cli list-users

# Run Polylith checks
clojure -M:poly check
```

### Environment Setup

Dependencies come from devenv, entered by direnv:

```bash
direnv allow          # once
devenv shell          # or just cd, with direnv active
```

`devenv.nix` sets the two values that have to be absolute paths,
`EXTENSIONS_LIBRARY_PATH` and `SEPAL_DATA_HOME`, so they follow the project
directory instead of breaking when it moves. Don't set those in `.env.local`.
`bin/reset-db.sh` carries `MIGRATIONS_DIR` and `SCHEMA_DUMP_FILE` itself,
derived from its own location; nothing reads them from the environment.

Copy `.env.local.example` to `.env.local` for everything else. Only
`SEPAL_SECRET` is required. The full variable list, with defaults, is in
`README.md`; the three worth knowing here are:
- `SEPAL_SECRET` - The master secret everything else is derived from. Was
  called `COOKIE_SECRET` until it came to cover the token secret too
- `WFO_DATABASE_PATH` - World Flora Online database, read by `bin/reset-db.sh`
- `MIGRATE_SH` - Absolute path to the sqlite-migrate script, same

`sepal.app.main/-main` passes `SEPAL_SECRET` to the instance API as the *master
secret*, and the cookie key and token secret are HKDF-derived from it per
instance. `ProcessOpts` requires at least 16 characters; there is no upper
bound, and the old exactly-16 rule went away with `system.edn`. `TOKEN_SECRET`
went with it — nothing reads it now, and
`main-test/test-token-secret-is-derived-not-read` exists to keep it from
coming back. One consequence worth knowing before you upgrade an install that
predates the derivation: the derived cookie key is not the old literal one, so
every session is invalidated once.

**Don't quote values in `.env.local`, and don't use `$HOME` or `${PWD}`.**
devenv's dotenv reader is not a shell: it keeps quotes as part of the value and
does not expand variables. `SEPAL_SECRET="..."` arrives 18 characters long, and
a quoted path arrives with the quotes attached.

Two gates are worth knowing because an empty value is not the same as an unset
one. `env-opts` builds `:smtp` only when `SMTP_HOST` is truthy and `:s3` only
when `AWS_ACCESS_KEY_ID` is truthy, and `""` is truthy in Clojure — so a blank
assignment in `.env.local` turns the subsystem on with unusable settings rather
than leaving it off. Comment the line out instead.

## Configuration

- **Main Configuration**: `sepal.app.instance` is the single definition of the system. It builds two [Integrant](https://github.com/weavejester/integrant) configs, split by lifetime: `start-process!` builds what exists once per JVM, and `start!` builds one instance. Both take an options map validated against the closed `ProcessOpts` and `InstanceOpts` schemas. There is no configuration file.
- **Environment**: `sepal.app.main/env-opts` maps an environment map to those options, and is the only place Sepal reads environment variables. It takes the map as an argument, so it is tested without mutating the process environment.
- **Callers**: `-main` (self-hosted), `development/src/user.clj` (REPL), `sepal.app.cli` (a small pool only), the test and e2e fixtures, and the control-plane dispatcher all go through that one vocabulary.
- **Database Configuration**: Database path defaults to `$SEPAL_DATA_HOME/sepal.db`. Pragmas and the SpatiaLite extension come from `sepal.database.interface/hikari-spec`, so every connection pool in the process opens a database the same way. Tests use temporary files.

## Code Patterns

### Store Interface (CRUD)

Use `sepal.store.interface` for standard CRUD operations:
```clojure
(store.i/get-by-id db :accession id spec/Accession)
(store.i/create! db :accession data spec/CreateAccession spec/Accession)
(store.i/update! db :accession id data spec/UpdateAccession spec/Accession)
```

### Malli Specs

Define input/output schemas with encode/decode transformers:
```clojure
(def CreateAccession
  [:map {:closed true}
   [:code [:string {:min 1}]]
   [:taxon-id {:decode/store validate.i/coerce-int} pos-int?]])
```

### Test Fixtures

Use `tf/testing` macro with Integrant-based fixtures:
```clojure
(use-fixtures :once default-system-fixture)

(deftest test-create
  (tf/testing "description"
    {[::taxon.i/factory :key/taxon] {:db *db*}
     [::acc.i/factory :key/acc] {:db *db* :taxon (ig/ref :key/taxon)}}
    (fn [{:keys [taxon acc]}]
      (is (match? expected result)))))
```

Components provide `::interface/factory` Integrant keys for test data generation.

### App Test Helpers

The `sepal.app.test` namespace provides HTTP testing utilities:
```clojure
(require '[sepal.app.test :as app.test])

;; Login and get a session
(let [sess (app.test/login email password)]
  (-> sess
      (peri/request "/some/path")))
```

### Routes

Routes are defined per-resource in `bases/app/src/sepal/app/routes/<resource>/`:
- `core.clj` - Route definitions
- `index.clj`, `create.clj`, `detail.clj` - Handlers

`routes/setup/` is the first-run wizard, and is how a standalone install gets
its first admin user and its taxon data: the taxonomy step reads the
`sepal-init-manifest.json` published on the GitHub releases page, downloads the
matching init database and imports from it (`routes/setup/shared.clj`). That
init database is built by `bin/create-init-db.sh` and published by
`bin/publish-sepal-init.sh`. `bin/reset-db.sh` is the other path to taxa, for a
development database rebuilt from a full WFO Plantlist.

### Frontend Assets

Frontend code is in `bases/app/src/sepal/app/`:
- `ui/page.ts` - Global Alpine/HTMX setup
- `routes/<resource>/form.ts` - Page-specific scripts
- `js/` - Shared directives and components

**Build commands must be run from `bases/app/`:**
```bash
cd bases/app
npm run build    # Production build
npm run dev      # Dev server with HMR
```

Changes to TypeScript require rebuild; changes to Clojure are hot-reloaded in the REPL.

### HTMX and Alpine.js

The frontend uses HTMX for server-driven interactivity and Alpine.js for client-side state.

**Key patterns:**
- HTMX must be imported in page-specific scripts (not just page.ts) to ensure DOM processing
- Use `$el.requestSubmit()` for form submission (not `$el.submit()`) so HTMX can intercept
- Handle non-2xx responses with `htmx:beforeSwap` event (configured in `ui/page.ts`)

### Form Validation

Use `sepal.validation.interface` for form validation. Forms use `hx-swap="none"` with out-of-band error swaps:
```clojure
(let [result (validation.i/validate-form-values FormSchema form-params)]
  (if (error.i/error? result)
    (http/validation-errors (validation.i/humanize result))  ; 422 with OOB errors to #field-errors
    (do-something-with result)))
```

**Empty String Handling:** Use `validation.i/empty->nil` decoder for optional fields:
```clojure
(def FormParams
  [:map {:closed true}
   [:name [:string {:min 1}]]                                         ; required
   [:email {:decode/form validation.i/empty->nil} [:maybe :string]]   ; optional
   [:id-qualifier {:decode/form validation.i/empty->nil} [:maybe accession.spec/id-qualifier]]])
```

### HTML Rendering

Use Chassis for HTML generation:
```clojure
(html/render-partial
  [:div {:class (html/attr "flex" "gap-2")}
   [:span "Content"]])
```

## Database

- **SQLite** with JSON columns and FTS5 for full-text search
- **SpatiaLite** extension for geo-coordinates
- Migrations in `components/database/resources/database/migrations/` (plain SQL files)
- Schema in `components/database/resources/database/schema.sql`
- Both are classpath resources, so a jar carries them; `sepal.database.migrate`
  enumerates the migrations directory from the classpath rather than from an index file

**`schema.sql` is generated, not hand-edited.** It is the baseline
`instance/provision!` loads and `bin/reset-db.sh` applies migrations on top of,
so it has to match what the migrations produce — and it carries the
`schema_version` rows that tell the runner nothing is pending. Regenerate it
with `bin/dump-schema.sh <db-path>` against a fully migrated database. The script
is `.schema` rather than `.dump`, because a dump emits the FTS5 shadow tables and
toggles `writable_schema`, neither of which belongs in a baseline.

Two things to know before you trust the output:

- **`.schema` emits no data.** A migration that seeds a table — `taxon_rank`, for
  instance — needs its `INSERT` appended by hand, or a freshly provisioned
  database gets the table and none of its rows.
- **A rebuilt table comes back quoted.** `ALTER TABLE ... RENAME TO` rewrites the
  stored DDL, so a table that went through the 12-step rebuild reads
  `CREATE TABLE "taxon"`. That is expected; don't hand-edit it back.

Adding a migration invalidates `schema.sql`, and two tests will tell you so:
`migrate-test/test-schema-version-and-latest` and
`instance-test/test-schema-lifecycle-through-the-instance-api` both compare a
schema-loaded database against `latest-version`. Regenerate in the same commit
as the migration.

### Schema compatibility (N-1)

Sepal refuses a database only when it is **below** `minimum-supported-version` in
`components/database/src/sepal/database/migrate.clj`. A database at or above the
floor must work against this code, including one that is *ahead* of it after a
rollback.

This is a constraint on the code. When you add a migration:

- **Do not assume the column is there.** Code that touches something added after
  the floor must check `schema-version` against the version that added it and fall
  back. `select new_col from t` fails outright on a database without it; SQL does
  not return null for a column that does not exist.
- **Prefer additive migrations.** Adding a table, column or index keeps old code
  working. Dropping or renaming breaks it, and breaks rollback with it.
- **CI runs the unit suite twice**, at the latest schema and at the floor, via the
  `schema` matrix in `.github/workflows/test.yml`. Reproduce the second locally
  with `SEPAL_TEST_SCHEMA_VERSION=floor clojure -M:dev:test:test-runner --focus :unit`.
- **Bumping the floor drops support.** Only do it deliberately, and only once no
  database you still need to open is below the new value. One that is will not
  start at all.

Why the floor exists: a database is not always migrated by the build that starts
it. Requiring an exact match makes any such database unstartable, with no way
back; a floor keeps it serving and leaves migrating it a separate step.

Tables: `user`, `taxon`, `accession`, `material`, `location`, `media`, `media_link`, `activity`, `contact`, `collection`, `settings`

### Geo-coordinates (SpatiaLite)

The project uses SQLite with the SpatiaLite extension for spatial data. Geo-coordinates are stored as GeoJSON with SRID (spatial reference ID).

**Environment setup:**
- `EXTENSIONS_LIBRARY_PATH` must point at the directory containing `mod_spatialite`
- `devenv.nix` sets it to `${pkgs.libspatialite}/lib`, so it works out of the box in the dev shell

**Coordinate reference systems:**
- Defined in `sepal.collection.interface.datum` with EPSG SRID codes
- Default is WGS-84 (SRID 4326)

## Style Guide

- Namespace aliases: `<component>.i` (e.g., `acc.i`, `taxon.i`)
- Format: Cursive-style function argument indentation
- Sorted ns requires (`cljfmt :sort-ns-references? true`)
- Test namespaces end in `-test`

### Styling

Three CSS files in `bases/app/src/sepal/app/css/`: `tokens.css` holds the
palette, type scale and radii; `components.css` holds the `spl-` component
layer; `main.css` wires them together. Colours, spacing and radii live in
`tokens.css` and nowhere else.

`bases/app/test/sepal/app/ui/no_daisyui_test.clj` enforces that with the unit
suite, reading the sources rather than rendered output — a class on a branch no
test exercises is exactly what drifts. It fails on a DaisyUI class, a hardcoded
palette colour, an opacity-suffixed theme colour, a `spl-` class with no rule,
a `daisyui` dependency, or a resurrected `tailwind.config.js`.

Scientific names render through `sepal.taxon.interface.name/segments`, which
splits the italic parts from the upright ones and returns data;
`bases/app/src/sepal/app/ui/taxon_name.clj` turns those segments into markup.
Never format a name by hand — the convention has to be identical in a table
cell, a panel, a breadcrumb and the activity feed.

## External Documentation

For AI assistants working with this codebase:
- **TailwindCSS**: https://tailwindcss.com/docs
