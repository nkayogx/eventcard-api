# EventCard — backend API

EventCard lets many event companies ("vendors") send digital invitation cards over
WhatsApp, SMS and links. This project is the **backend** (Spring Boot, Java 17).
The React website lives next to it in `../eventcard-web`.

Designs: [multi-tenant core](docs/superpowers/specs/2026-09-21-multi-tenant-core-design.md) · [events & guests](docs/superpowers/specs/2026-09-21-events-and-guests-design.md) · [digital cards](docs/superpowers/specs/2026-09-21-card-design-design.md)

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
| `UPLOADS_FOLDER` | where uploaded logos and card artwork are saved (publicly downloadable) |
| `CARD_CACHE_FOLDER` | where finished guest cards are kept — **private**, never serve it publicly |
| `CUSTOM_DOMAIN_TARGET` | the host name vendors point their custom domain to (CNAME) |
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
| `event/` | events, card types (Single, Double, VIP…), totals |
| `guest/` | guest lists, Excel/CSV upload, template download |
| `card/` | card designs, templates, drawing card images and QR codes |
| `invitation/` | the guest's public invitation page and RSVP (no login) |
| `platform/` | the platform admin's screens (all companies, suspend, allow sending) |
| `tenant/` | **keeps each company's data separate** — start with `CurrentTenant.java` |
| `storage/` | saving uploaded files |
| `common/` | error messages and small shared helpers |

### How companies are kept apart

Every table that belongs to a company has a `company_id` column marked `@TenantId`.
Hibernate automatically adds "only this company's rows" to every database query,
using the company of the logged-in user. If code ever forgets to choose a company,
it sees **nothing** rather than everything. See `tenant/HibernateTenantSetup.java`.

## Custom domains for vendors (hosting)

A vendor's own domain (e.g. `invites.kayoevents.com`) points to `CUSTOM_DOMAIN_TARGET`
with a CNAME record. The React website must then answer on that domain with HTTPS.
The simplest way is a Caddy web server with *on-demand TLS*, which gets a free
certificate for each vendor domain the first time a guest visits:

```
{
    on_demand_tls {
        # Caddy asks our API before getting a certificate, so only real vendor domains get one
        ask http://localhost:8181/api/public/domains/allowed
    }
}
https:// {
    tls { on_demand }
    reverse_proxy localhost:4173   # the built React website
}
```

The `ask` address answers 200 only for vendors' **verified** custom domains, so nobody
can make Caddy request certificates for random domains.
