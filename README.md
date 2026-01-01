# Sepal

A botanical collection management system for managing plant accessions, materials, locations, and taxonomic data. Integrates with the [World Flora Online](https://www.worldfloraonline.org/) Plantlist for taxonomy reference.

## Features

- Plant accession tracking
- Material management
- Location management
- Taxonomic data with WFO Plantlist integration
- Media attachments
- Full-text search

## Getting Started

### Prerequisites

- Clojure 1.12+
- Node.js 22.x (for frontend build)
- sqlite3 (for database operations)
- SpatiaLite extension (for geo-coordinate support)
- [sqlite-migrate](https://github.com/brettatoms/sqlite-migrate)  (optional, for applying new migrations)

**Using devbox (recommended):** All prerequisites including SpatiaLite are installed automatically via `devbox shell`.

### WFO Plantlist Database

Sepal uses a SQLite version of the World Flora Online Plantlist for taxonomy data.

- Download pre-built database: [10.5281/zenodo.17444674](https://doi.org/10.5281/zenodo.17444674)
- Or build your own: [wfo-plantlist-sqlite](https://github.com/brettatoms/wfo-plantlist-sqlite)

### Environment Variables

Copy `.env.local.example` to `.env.local` and configure the following:

**Required:**

| Variable | Description |
|----------|-------------|
| `COOKIE_SECRET` | Secret for encrypting cookies |
| `TOKEN_SECRET` | Secret for encrypting tokens (password reset, invitations) - min 16 chars |
| `APP_DOMAIN` | Domain for email links (e.g., reset password) |
| `SMTP_HOST` | SMTP server hostname |
| `SMTP_PORT` | SMTP server port (e.g., 587 for STARTTLS, 465 for SSL) |
| `SMTP_USERNAME` | SMTP authentication username |
| `SMTP_PASSWORD` | SMTP authentication password |
| `SMTP_AUTH` | Enable SMTP authentication (true/false) |
| `SMTP_TLS` | TLS mode: `starttls`, `ssl`, or `none` |

**Database:**

| Variable | Description |
|----------|-------------|
| `WFO_DATABASE_PATH` | Path to WFO Plantlist SQLite database (only needed for DB initialization) |
| `SEPAL_DATA_HOME` | Data directory for database and config files (defaults to `$XDG_DATA_HOME/Sepal` or platform equivalent) |
| `EXTENSIONS_LIBRARY_PATH` | Path to directory containing SQLite extensions like `mod_spatialite` (see troubleshooting below) |

**Optional - Media Uploads:**

| Variable | Description |
|----------|-------------|
| `MEDIA_UPLOAD_BUCKET` | S3 bucket for media uploads |
| `IMGIX_MEDIA_DOMAIN` | Imgix domain for serving media |
| `AWS_S3_ENDPOINT` | S3-compatible endpoint |
| `AWS_ACCESS_KEY_ID` | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key |
| `AWS_REGION` | AWS region |

**Optional - Email Customization:**

| Variable | Description |
|----------|-------------|
| `FORGOT_PASSWORD_EMAIL_FROM` | Reply-to address for password reset emails |
| `FORGOT_PASSWORD_EMAIL_SUBJECT` | Subject line for password reset emails |

### Development Setup

1. Enter devbox shell: `devbox shell` (installs all dependencies including SpatiaLite)
2. Download the WFO Plantlist database (see above)
3. Copy `.env.local.example` to `.env.local` and configure variables
4. Run `bin/reset-db.sh` to initialize database with WFO data
5. Create an admin user (see User Management below)
6. Start REPL and run `(go)` - zodiac-asset handles npm install and Vite automatically

### User Management

User registration is disabled. Users must be created via the CLI.

**Roles:**
- `admin` - Full access: organization settings, user management, all CRUD operations
- `editor` - Can create/edit/delete plant records, edit own profile
- `reader` - View-only access to plant records, edit own profile

**Create a user:**
```bash
clojure -M:dev:cli create-user --email admin@example.com --password secret --role admin
```

**List all users:**
```bash
clojure -M:dev:cli list-users
```

**Create admin user during database reset:**
```bash
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=secret bin/reset-db.sh
```

**Troubleshooting SQLite extensions:** If you encounter errors loading SQLite extensions (like SpatiaLite), set `EXTENSIONS_LIBRARY_PATH` to the directory containing the extension libraries. With devbox, this is typically `${PWD}/.devbox/nix/profile/default/lib`. See `.env.local.example` for an example.

### Production Build

1. Build frontend assets: `cd bases/app && npm run build`
2. Build uberjar or deploy as appropriate
