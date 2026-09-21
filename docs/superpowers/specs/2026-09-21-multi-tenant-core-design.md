# Multi-Tenant Core — Design (Sub-project 1 of 7)

Date: 2026-09-21
Status: Approved in brainstorming, awaiting written-spec review

## 1. Context and goal

EventCard is a SaaS platform where many vendors (event companies) send digital
invitation cards to guests over WhatsApp, SMS and web links.

The full product is split into 7 sub-projects, each with its own design → plan → build cycle:

1. **Multi-tenant core** ← *this document*
2. Events and guests
3. Digital cards (templates, personal links, QR, RSVP, custom-domain serving)
4. Delivery (WhatsApp / SMS providers, delivery status)
5. Check-in at the door (QR scanning)
6. SaaS billing (plans, message credits, payments)
7. (React frontend grows alongside each sub-project)

**Goal of this sub-project:** vendors can sign up, manage their company profile
and staff, and every piece of data is strictly separated per company. A platform
admin (the SaaS owner) can oversee all companies.

### Guiding principle: readable code

Code must be understandable even by a non-expert:
- plain, descriptive names — no abbreviations (`companyRepository`, not `compRepo`)
- small methods that do one thing
- short comments in simple English explaining *why*
- no clever tricks; the one piece of "magic" (automatic tenant filtering) lives in
  one clearly commented place

## 2. Decisions made

| Topic | Decision |
|---|---|
| Tenant storage | One shared PostgreSQL database; every tenant table has `company_id` |
| User ↔ company | Each user belongs to exactly one company (platform admin: none) |
| Roles | Fixed roles: `OWNER`, `MANAGER`, `CHECK_IN_STAFF`, `PLATFORM_ADMIN` |
| Vendor signup | Instant self-service; message sending locked until admin verifies |
| Tenant enforcement | Hibernate built-in `@TenantId` (automatic filter on reads and writes) |
| Frontend | Separate React app (Vite + TypeScript) in sibling folder `eventcard-web/` |
| Tests | JUnit + Spring Boot Test against PostgreSQL in Docker (Testcontainers); falls back to in-memory H2 when Docker is not available |

## 3. Overall structure

### Backend (this Spring Boot project)

- Serves only a JSON REST API under `/api/...`.
- Remove leftover tutorial code: `School`, `Student`, their DTOs, controllers,
  repositories, and `static/index.html`.
- Remove the old `Role` / `Permission` entities and tables — replaced by a single
  `role` column on `User`.
- Merge `application.properties` and `application.yml` into **one**
  `application.yml`. Secrets (DB password, JWT secret) come from environment
  variables, with local-development defaults.
- CORS enabled for the React app's origin (configurable, default `http://localhost:5173`).

Code is grouped **by feature**, not by technical layer:

```
com.kayogx.eventcard
├── company/     Company entity, CompanyController, CompanyService, CompanyRepository,
│                custom domain verification, logo upload
├── user/        User entity, UserRole enum, StaffInvitation, staff management
├── auth/        signup, login, JwtService, JwtAuthFilter
├── tenant/      CurrentTenant holder, Hibernate tenant resolver
├── platform/    platform-admin endpoints (list / suspend / allow sending)
├── storage/     FileStorage interface + LocalFileStorage implementation
└── common/      GlobalErrorHandler, ErrorResponse, shared base classes
```

### Frontend (new, `../eventcard-web/`)

Vite + React + TypeScript, React Router, TanStack Query for API calls,
Tailwind CSS for styling.

## 4. Data model

All primary keys are UUIDs, so IDs cannot be guessed in URLs.

### `companies`

| Column | Type / rule | Meaning |
|---|---|---|
| id | UUID, PK | |
| name | text, required | "Kayo Events" |
| slug | text, required, unique, lowercase letters/digits/hyphens | `kayo-events`, used in links |
| logo_url | text, optional | where the uploaded logo is stored |
| contact_phone | text, required | international format, e.g. `+255712345678` |
| contact_email | text, required | |
| address | text, optional | |
| city | text, optional | |
| country_code | 2 letters, required | e.g. `TZ` |
| time_zone | text, required | e.g. `Africa/Dar_es_Salaam` |
| primary_color | `#RRGGBB`, optional | brand colour |
| secondary_color | `#RRGGBB`, optional | brand colour |
| custom_domain | text, optional, unique | e.g. `invites.kayoevents.com` |
| custom_domain_verified | boolean, default false | |
| domain_verification_code | text, optional | value the vendor puts in a DNS TXT record |
| account_status | `ACTIVE` / `SUSPENDED`, default `ACTIVE` | |
| can_send_messages | boolean, default false | switched on by platform admin |
| created_at, updated_at | timestamps | |

The `companies` table itself is **not** tenant-filtered — a company *is* the
tenant. Company endpoints load the row by the `companyId` of the logged-in user.

### `users` (tenant-filtered via `company_id`)

| Column | Type / rule | Meaning |
|---|---|---|
| id | UUID, PK | |
| company_id | UUID, `@TenantId` | for `PLATFORM_ADMIN` holds the special value `00000000-0000-0000-0000-000000000000` meaning "all companies" |
| full_name | text, required | |
| email | text, required, unique across platform | login name |
| phone | text, optional | |
| password_hash | text, required | BCrypt |
| role | enum, required | `OWNER`, `MANAGER`, `CHECK_IN_STAFF`, `PLATFORM_ADMIN` |
| is_active | boolean, default true | owner can deactivate staff |
| created_at | timestamp | |

### `staff_invitations` (tenant-filtered via `company_id`)

| Column | Meaning |
|---|---|
| id | UUID |
| company_id | `@TenantId` |
| email, role | who is invited and as what (see rule below) |
| code | long random one-time code (unique) |
| expires_at | created time + 7 days |
| accepted_at | set when used; used codes cannot be reused |
| invited_by_user_id | the owner who sent it |

Invitations may grant `OWNER`, `MANAGER` or `CHECK_IN_STAFF` — never `PLATFORM_ADMIN`.

### Business rules

- A company always has **at least one active OWNER**. Demoting or deactivating
  the last owner is refused with `409`.
- A user cannot deactivate or demote themselves (prevents accidental lock-out).
- Email, slug and custom domain are unique across the whole platform.
- Slug is generated from the company name at signup (e.g. "Kayo Events" →
  `kayo-events`); if taken, a number is added (`kayo-events-2`). Owner may edit it later.

### Who can do what

| Action | OWNER | MANAGER | CHECK_IN_STAFF | PLATFORM_ADMIN |
|---|---|---|---|---|
| View own company profile | ✔ | ✔ | ✔ | — |
| Edit profile, logo, colours, domain | ✔ | | | |
| View staff list | ✔ | ✔ | | |
| Invite / change / deactivate staff | ✔ | | | |
| List / suspend / allow-sending for any company | | | | ✔ |

(Event, guest, sending and check-in permissions are defined in later sub-projects.)

### Platform admin account

The first platform admin is created at startup by a seeder from environment
variables (`PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_PASSWORD`) if no admin exists yet.

## 5. API

All request/response bodies are JSON. Dates are ISO-8601.

### Public (no login)

| Method & path | Purpose |
|---|---|
| `POST /api/auth/signup-company` | body: companyName, fullName, email, phone, password, countryCode, timeZone → creates company + OWNER user, returns token |
| `POST /api/auth/login` | body: email, password → returns token |
| `GET /api/invitations/{code}` | shows company name, email and role of the invite |
| `POST /api/invitations/{code}/accept` | body: fullName, phone, password → creates user, returns token |

Passwords: at least 8 characters.

### Logged-in company users

| Method & path | Who |
|---|---|
| `GET /api/me` | anyone logged in — returns user, role, company summary |
| `GET /api/my-company` | OWNER, MANAGER, CHECK_IN_STAFF |
| `PUT /api/my-company` | OWNER |
| `POST /api/my-company/logo` (multipart, PNG/JPG/SVG, max 2 MB) | OWNER |
| `POST /api/my-company/custom-domain` body: domain → returns TXT record name & value | OWNER |
| `POST /api/my-company/custom-domain/verify` → looks up DNS TXT record | OWNER |
| `DELETE /api/my-company/custom-domain` | OWNER |
| `GET /api/my-company/staff` | OWNER, MANAGER |
| `POST /api/my-company/staff/invitations` body: email, role | OWNER |
| `PUT /api/my-company/staff/{userId}` body: role and/or isActive | OWNER |

Custom domain verification: TXT record at `_eventcard.<domain>` must equal
`domain_verification_code`. Changing the domain resets `custom_domain_verified`
to false and generates a new code. Actually *serving* cards on the domain
(routing, SSL) is part of sub-project 3.

Invitation email sending: for this sub-project the API **returns the invite
link** in the response so the owner can share it (copy / WhatsApp). Automatic
email delivery comes with the delivery sub-project.

### Platform admin

| Method & path |
|---|
| `GET /api/platform/companies` (paged, optional search by name) |
| `PUT /api/platform/companies/{id}/suspend` |
| `PUT /api/platform/companies/{id}/reactivate` |
| `PUT /api/platform/companies/{id}/allow-sending` |
| `PUT /api/platform/companies/{id}/block-sending` |

## 6. How tenant isolation works

1. Login token (JWT, HS256, valid 12 hours) carries `userId`, `companyId`, `role`.
2. `JwtAuthFilter` validates the token, loads the user, and:
   - rejects the request with `401` if the user is inactive;
   - rejects with `403 "Your company account is suspended"` if the company is suspended;
   - stores the company id in `CurrentTenant` for this request (cleared afterwards).
3. A Hibernate `CurrentTenantIdentifierResolver` reads `CurrentTenant`. Every
   entity with a `@TenantId` field is automatically filtered by it on reads and
   stamped with it on inserts.
4. A platform admin is the Hibernate **root tenant** (`isRoot` returns true), so
   admin queries see all companies.
5. Code that must look up data before a tenant is known (login, invitation
   lookup, the JWT filter, the startup seeder) runs inside a clearly named helper
   `CurrentTenant.runAsAllCompanies(...)`.
6. **Fail-safe default:** when no company has been set for the current request,
   queries run with a "no company" value that matches no rows — forgetting to
   set the tenant shows nothing rather than everything.

Records from another company are simply invisible, so they return `404` — we
never reveal that they exist.

## 7. Error handling

One `GlobalErrorHandler` returns every error in this shape:

```json
{ "message": "This email is already registered", "field": "email" }
```

(`field` is omitted when not about a specific input.)

| Status | When |
|---|---|
| 400 | invalid input — plain-language message per field |
| 401 | not logged in, bad credentials, expired token, inactive user |
| 403 | role not allowed, or company suspended |
| 404 | not found, or belongs to another company |
| 409 | duplicate email / slug / domain; last-owner rule; used or expired invitation |

## 8. React screens (this sub-project)

- Sign up company, Log in, Accept invitation
- Company profile: edit fields, logo upload, two colour pickers with live preview
- Custom domain: DNS instructions + "Verify" button + status
- Staff: list, invite (shows copyable invite link), change role, deactivate
- Platform admin: companies table with Suspend / Reactivate / Allow / Block sending
- App layout with role-based menu and a "Sending locked until your company is
  verified" banner when `canSendMessages` is false
- Token kept in memory + `localStorage`; on `401` the user is sent to Log in

## 9. Testing

JUnit 5 + Spring Boot Test against a **real PostgreSQL database running in
Docker**, started and stopped automatically by **Testcontainers**
(`org.testcontainers:postgresql`, image `postgres:16-alpine`). Spring Boot's
`@ServiceConnection` wires the container into the app, so tests need no manual
database settings.

- One shared container for the whole test run (started once, reused by every
  test class) to keep tests fast.
- Each test creates its own fresh companies with unique emails, so tests never affect each other.
- **Fallback:** if Docker is not installed or not running, the tests
  automatically use an in-memory H2 database (PostgreSQL compatibility mode)
  instead, and print which database was chosen. No manual switch needed.

Required tests:
- **Tenant isolation:** two companies; company A users cannot list, read or
  edit company B's staff, invitations or profile.
- Signup creates company + owner; duplicate email → 409; slug de-duplication.
- Invitation: accept works once; expired or reused code → 409.
- Last-owner rule and self-demotion rule.
- Role checks: MANAGER cannot invite (403); non-admin on `/api/platform/**` → 403.
- Suspended company → 403 on every endpoint.
- Custom domain verify with DNS lookup behind an interface (faked in tests).

## 10. Out of scope (later sub-projects)

Events, guests, card designs, WhatsApp/SMS sending, email sending, check-in,
billing, serving cards on custom domains, refresh tokens, password reset.
