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
- dbmate (with FTS5 support)

### WFO Plantlist Database

Sepal uses a SQLite version of the World Flora Online Plantlist for taxonomy data.

- Download pre-built database: [10.5281/zenodo.17444674](https://doi.org/10.5281/zenodo.17444674)
- Or build your own: [wfo-plantlist-sqlite](https://github.com/brettatoms/wfo-plantlist-sqlite)

### Environment Variables

Copy `.envrc.example` to `.envrc` and configure the following:

**Required:**

| Variable | Description |
|----------|-------------|
| `COOKIE_SECRET` | Secret for encrypting cookies |
| `RESET_PASSWORD_SECRET` | Secret for encrypting password reset tokens |
| `APP_DOMAIN` | Domain for email links (e.g., reset password) |
| `POSTMARK_API_KEY` | Postmark API key for sending emails |

**Database:**

| Variable | Description |
|----------|-------------|
| `WFO_DATABASE_PATH` | Path to WFO Plantlist SQLite database (only needed for DB initialization) |
| `DATABASE_JDBC_URL` | JDBC connection string (optional, defaults to user's data dir) |

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

1. Download the WFO Plantlist database (see above)
2. Copy `.envrc.example` to `.envrc` and configure variables
3. Run `bin/reset-db.sh` to initialize database with WFO data
4. Start REPL and run `(go)` - zodiac-asset handles npm install and Vite automatically

### Production Build

1. Build frontend assets: `cd bases/app && npm run build`
2. Build uberjar or deploy as appropriate
