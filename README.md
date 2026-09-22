# EventCard — backend API

EventCard lets many event companies ("vendors") send digital invitation cards over
WhatsApp, SMS and links. This project is the **backend** (Spring Boot, Java 17).
The React website lives next to it in `../eventcard-web`.

Designs: [multi-tenant core](docs/superpowers/specs/2026-09-21-multi-tenant-core-design.md) · [events & guests](docs/superpowers/specs/2026-09-21-events-and-guests-design.md) · [digital cards](docs/superpowers/specs/2026-09-21-card-design-design.md) · [plans & payments](docs/superpowers/specs/2026-09-22-billing-design.md) · [sending cards](docs/superpowers/specs/2026-09-22-sending-design.md) · [check-in](docs/superpowers/specs/2026-09-22-check-in-design.md)

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
| `PAYMENT_PAY_TO_NAME`, `PAYMENT_PAY_TO_NUMBER`, `PAYMENT_NETWORKS` | your Lipa Namba / till details, shown to vendors when they pay |
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
| `messaging/` | sending cards by WhatsApp/SMS: the queue, the background worker, delivery reports (start with `MessageWorker.java`) |
| `checkin/` | letting guests in at the door: scan look-up, check-in with seat counting, undo, live numbers |
| `billing/` | plans and their limits, message credits, payments (start with `PlanLimits.java` and `CreditAccount.java`) |
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

## Payments

Vendors pay by mobile money to your Lipa Namba / till, then type the transaction code
into the app. You check the money arrived and press **Confirm** under
*Payments* in the platform admin area; the plan or credits switch on at once.

To take payments automatically later (AzamPay, Selcom, ClickPesa...), add a class that
implements `billing/PaymentProvider.java` and calls `PaymentConfirmer.confirm(...)` when the
payment company reports the money arrived. Nothing else needs to change.

## Sending cards (WhatsApp & SMS)

Out of the box the app only **pretends** to send (messages are written to the log and marked
delivered), so nothing costs money while you try things out. Pretend phone numbers ending in
`0000` fail on WhatsApp, `1111` fail on SMS and `9999` fail temporarily - handy for demos.

To send for real, set these environment variables:

| Variable | What it is |
|---|---|
| `WHATSAPP_PROVIDER=meta` | switch WhatsApp from pretend to Meta's WhatsApp Cloud API |
| `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN` | from your Meta WhatsApp Business app |
| `WHATSAPP_APP_SECRET` | used to check delivery reports really come from Meta |
| `WHATSAPP_VERIFY_TOKEN` | any secret word; enter the same one in Meta's webhook settings |
| `WHATSAPP_TEMPLATE_SW`, `WHATSAPP_TEMPLATE_EN` | names of your approved templates (default `eventcard_invitation_sw` / `_en`) |
| `SMS_PROVIDER=beem` | switch SMS from pretend to Beem Africa |
| `BEEM_API_KEY`, `BEEM_SECRET_KEY` | from your Beem account |
| `SMS_SENDER_NAME` | your registered platform sender name (default `EVENTCARD`) |
| `BEEM_WEBHOOK_TOKEN` | any secret word, used in the delivery report address below |

**WhatsApp templates** to submit for approval in Meta Business Manager (category *Utility*),
each with an **image header** and this body (`{{1}}` name, `{{2}}` company, `{{3}}` event,
`{{4}}` date, `{{5}}` link):

- `eventcard_invitation_en` (English): `Hello {{1}}, {{2}} invites you to {{3}} on {{4}}. Your card and RSVP: {{5}}`
- `eventcard_invitation_sw` (Swahili): `Habari {{1}}, {{2}} inakualika kwenye {{3}}, {{4}}. Kadi yako na RSVP: {{5}}`

**Delivery report addresses** (must be reachable from the internet):

- Meta webhook: `https://<your-api>/api/public/webhooks/whatsapp` (subscribe to `messages`)
- Beem delivery reports: `https://<your-api>/api/public/webhooks/beem?token=<BEEM_WEBHOOK_TOKEN>`

Beem's report format should be checked against their current documentation when setting up;
the app reads `request_id` and `status` (DELIVERED / UNDELIVERED / FAILED...).

## Check-in at the door

Staff open **Events → (event) → Open check-in** on their phone and scan each guest's QR code with
the camera, or search by name/phone. USB/Bluetooth barcode scanners also work: they type the code
into the page. Phones only allow the camera on **HTTPS** sites (or `localhost` while testing), so
the website must be served over HTTPS at the venue. Check-in needs an internet connection; each
scan is a tiny request, so weak mobile data is enough.
