# EventCard — backend API

EventCard lets many event companies ("vendors") send digital invitation cards over
WhatsApp, SMS and links. This project is the **backend** (Spring Boot, Java 17).
The React website lives next to it in `../eventcard-web`.

Design: [`docs/superpowers/specs/2026-09-21-multi-tenant-core-design.md`](docs/superpowers/specs/2026-09-21-multi-tenant-core-design.md)

## Run it on your computer

1. PostgreSQL must be running, with a database called `eventcard`:
   ```
   psql -U postgres -c "CREATE DATABASE eventcard"
   ```
2. Start the backend (tables are created automatically):
   ```
   ./mvnw spring-boot:run
   ```
   It runs at http://localhost:8181. A first **platform admin** account is created:
   `admin@eventcard.local` / `ChangeMe123!` (change these, see below).
3. Start the website:
   ```
   cd ../eventcard-web
   npm install
   npm run dev
   ```
   Open http://localhost:5173 and sign up a company.

## Settings

All settings are in `src/main/resources/application.yml`. In production, set these
environment variables instead of using the development defaults:

| Variable | What it is |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | secret for login tokens — a long random text (32+ characters) |
| `FRONTEND_URL` | address of the React website (for CORS and invitation links) |
| `PUBLIC_API_URL` | public address of this API (for logo links) |
| `UPLOADS_FOLDER` | where uploaded logos are saved |
| `PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_PASSWORD` | the first platform admin |

## Tests

```
./mvnw test
```

Tests use a real PostgreSQL in Docker when Docker is running, and an in-memory
H2 database otherwise — no setup needed either way.

## How the code is organised

Each folder is one topic:

| Folder | What's inside |
|---|---|
| `auth/` | signup, login, login tokens (JWT), security rules |
| `company/` | company profile, logo, brand colours, custom domain |
| `user/` | users, roles, staff list, invitations |
| `platform/` | the platform admin's screens (all companies, suspend, allow sending) |
| `tenant/` | **keeps each company's data separate** — start with `CurrentTenant.java` |
| `storage/` | saving uploaded files |
| `common/` | error messages and small shared helpers |

### How companies are kept apart

Every table that belongs to a company has a `company_id` column marked `@TenantId`.
Hibernate automatically adds "only this company's rows" to every database query,
using the company of the logged-in user. If code ever forgets to choose a company,
it sees **nothing** rather than everything. See `tenant/HibernateTenantSetup.java`.
