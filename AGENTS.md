# AGENTS.md

## Project Overview

Sepal is a botanical collection management system for managing plant accessions, materials, locations, and taxonomic data. It integrates with the World Flora Online (WFO) Plantlist database for taxonomy reference.

## Architecture

This is a **Polylith** monorepo with the following structure:

- `bases/app/` - The main web application (Ring/Reitit server with HTMX frontend)
- `components/` - Domain components with public interfaces
- `development/` - REPL development entry point
- `projects/` - Deployable artifacts
- `db/migrations/` - SQL migrations managed by dbmate

### Bases vs Components

**Components** (`components/`) contain reusable domain logic:
- Each component exposes functionality through `<component>.interface` namespace
- Components are reusable across different bases
- Domain logic: validation schemas, database queries, business rules
- Examples: `validation`, `user`, `accession`, `taxon`

**Bases** (`bases/`) contain application-specific code:
- `bases/app/` - The web application
  - Routes (`routes/<resource>/`)
  - UI helpers (`ui/`)
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

### Testing
- **Kaocha** - Test runner
- **matcher-combinators** - Assertion library
- **test.check/malli.generator** - Property-based testing
- **Peridot/Kerodon** - HTTP testing

### Tools
- **dbmate** - Database migrations
- **clj-kondo** - Linter
- **cljfmt** - Formatter

## Development

### REPL Startup

The `go`, `stop`, and `restart` functions for managing the system during development are defined in `development/src/user.clj`.

Start REPL with dev alias, then:
```clojure
(go)         ; Start system with :local profile
(stop)       ; Stop system
(restart)    ; Restart system
```

After starting the system with `(go)`, two dynamic vars become available in the `user` namespace for interactive development:
- `*system*`: An Integrant map containing all running components of the application.
- `*db*`: A `next.jdbc` database connection pool for direct database queries.

### Common Commands

```bash
# Run all tests
clojure -M:dev:test:test-runner

# Run specific test namespace
clojure -M:dev:test:test-runner --focus sepal.accession.interface-test

# Run specific test
clojure -M:dev:test:test-runner --focus sepal.accession.interface-test/test-create

# Lint
bin/lint
# or individually:
clj-kondo --parallel --lint components --lint bases --lint projects
clojure -M:cljfmt check

# Format code
clojure -M:cljfmt fix

# Reset database (loads WFO taxon data)
bin/reset-db.sh

# Run Polylith checks
clojure -M:poly check
```

### Environment Setup

Copy `.envrc.example` to `.envrc` and set:
- `DATABASE_JDBC_URL` - SQLite database path
- `WFO_DATABASE_PATH` - World Flora Online database
- `COOKIE_SECRET` - Session encryption (16+ chars)

Additional env vars for production (see `bases/app/resources/app/system.edn`):
- AWS credentials for S3/media storage
- Postmark API key for email
- `APP_DOMAIN`, `IMGIX_MEDIA_DOMAIN`

## Configuration

- **Main Configuration**: The primary system configuration is defined as an [Integrant](https://github.com/weavejester/integrant) file at `bases/app/resources/app/system.edn`.
- **Dynamic Setup with Aero**: The configuration is processed by [Aero](https://github.com/juxt/aero), which enables environment-specific setups using profiles (e.g., `:local`, `:default`, `:test`) and environment variable injection (e.g., `#env COOKIE_SECRET`).
- **Database Configuration**: Database connection details are specified in `components/database/resources/database/config.edn`. This file uses Aero profiles to switch the `jdbcUrl` between a file-based SQLite database for development (`jdbc:sqlite:sepal.db`) and an in-memory database for testing.

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

**SlimSelect autocomplete fields:**
- Alpine directives (`x-taxon-field`, `x-contact-field`) wrap SlimSelect
- SlimSelect hides the original `<select>` and creates visible `.ss-main` wrapper
- Directives include cleanup callbacks to destroy SlimSelect instances

### Form Validation

Use `sepal.validation.interface` for form validation:
```clojure
(let [result (validation.i/validate-form-values FormSchema form-params)]
  (if (error.i/error? result)
    (http/validation-errors (validation.i/humanize result))  ; 422 with OOB errors
    (do-something-with result)))
```

**OOB Error Swaps:**
- Form fields have error containers with IDs like `#code-errors`, `#taxon-id-errors`
- On validation failure, return 422 with `hx-swap-oob` error elements
- Form uses `hx-swap="none"` so only error containers are updated

### HTML Rendering

Use Chassis for HTML generation:
```clojure
(html/render-partial
  [:div {:class (html/attr "flex" "gap-2")}
   [:span "Content"]])
```

## Database

- **SQLite** with JSON columns and FTS5 for full-text search
- Migrations in `db/migrations/` (dbmate format)
- Schema dumped to `db/schema.sql`

Tables: `user`, `taxon`, `accession`, `material`, `location`, `media`, `media_link`, `activity`, `contact`, `settings`

## Style Guide

- Namespace aliases: `<component>.i` (e.g., `acc.i`, `taxon.i`)
- Format: Cursive-style function argument indentation
- Sorted ns requires (`cljfmt :sort-ns-references? true`)
- Test namespaces end in `-test`
