# Plans, Credits & Payments — Design (Sub-project 6 of 7)

Date: 2026-09-22
Status: Approved in brainstorming
Builds on: sub-projects 1–3

## 1. Goal

Vendors pay a **monthly plan** (which sets their limits and features) and buy
**message credits** in packs (spent later by WhatsApp/SMS sending, sub-project #4).
Payments go through a **payment connector**; the first connector is **manual**:
the vendor pays to the platform's Lipa Namba / till, enters the transaction code,
and the platform admin confirms. Real mobile-money connectors (AzamPay, Selcom,
ClickPesa…) plug in later without other changes.

## 2. Decisions made

| Topic | Decision |
|---|---|
| Pricing | Monthly plans (limits + features) **and** prepaid message credits |
| Payment | `PaymentProvider` connector; start with manual confirmation by platform admin |
| Plans & prices | Managed by the platform admin in the app (plans, credit packs, message prices) |
| Plan ends | 7 days' grace, then Free limits; nothing deleted; existing events keep working for guests; credits never expire |

## 3. Data model

### Platform-wide (not per company)

**`plans`**: id, code (unique, e.g. `PRO`), name, monthly_price_tzs, max_active_events,
max_guests_per_event, max_staff (empty = unlimited), allows_custom_domain,
allows_own_artwork, free_plan (exactly one), available, sort_order.

**`credit_packs`**: id, name, credits, price_tzs, available, sort_order.

**`message_prices`**: channel (`SMS`, `WHATSAPP`, unique), credits per message
(SMS: per 160-character part). Defaults: SMS 1, WhatsApp 2.

Starting data (created at startup only if no plans exist):

| Plan | TZS / month | Active events | Guests / event | Staff (incl. owner) | Custom domain | Own artwork |
|---|---|---|---|---|---|---|
| Free | 0 | 1 | 100 | 1 | – | – |
| Starter | 30,000 | 5 | 500 | 3 | – | ✔ |
| Pro | 80,000 | unlimited | 3,000 | 10 | ✔ | ✔ |

Credit packs: 100 credits / 6,000 · 500 credits / 25,000 · 2,000 credits / 90,000 TZS.

### Per company

- `companies` + `plan_id` (empty = Free), `plan_paid_until` (date), `credit_balance` (default 0).
- **`credit_movements`** (`@TenantId`), never changed after saving:
  amount (+/−), reason (`PURCHASE`, `MESSAGE_SENT`, `MESSAGE_REFUND`, `ADMIN_ADJUSTMENT`),
  balance_after, note, payment_id, created_at, created_by_user_id.
- **`payments`** (`@TenantId`): reference (unique, e.g. `EC-7K3P9Q`), type (`PLAN`/`CREDITS`),
  plan_id + months (1–12) or credit_pack_id + credits, description ("Pro plan – 3 months"),
  amount_tzs (price at ordering time), method (`MANUAL`), status
  (`WAITING_FOR_PAYMENT` → `PAID` | `REJECTED` | `CANCELLED`), payer_phone,
  transaction_reference, submitted_at, confirmed_by_user_id, confirmed_at, admin_note,
  created_by_user_id, created_at.

Credit changes lock the company row, so two changes at once cannot mis-count;
the balance can never go below zero.

## 4. Rules

**Which plan applies** (`CurrentPlan`, dates in the company's time zone):
- paid plan while `today ≤ plan_paid_until + 7 days`; otherwise the Free plan.
- Status shown to the vendor: `FREE`, `ACTIVE`, `IN_GRACE` (paid-until passed, within 7 days), `EXPIRED` (back on Free).

**Confirming a plan payment:** same plan still running (not past paid-until) →
months are added to paid-until; otherwise paid-until = today + months.

**Limits** (409 with a plain message and `field: "plan"`, so the website can show an "Upgrade" link):

| Action | Check |
|---|---|
| Change an event to ACTIVE | active events < max_active_events (drafts are unlimited) |
| Add a guest / import guests | guests in event + new ≤ max_guests_per_event (an import over the limit is refused as a whole) |
| Invite staff, accept an invitation, reactivate a staff member | active users < max_staff |
| Set a custom domain | allows_custom_domain |
| Upload own artwork / card-type artwork, switch design to own artwork | allows_own_artwork |

Nothing already there is deleted or blocked; guest pages, cards and RSVP always work.
The import preview shows how many more guests the plan allows.

## 5. API

### Vendor (view: OWNER, MANAGER · buy: OWNER)

| Method & path | Notes |
|---|---|
| `GET /api/billing` | plan + status + paid-until, usage vs limits, credit balance, plans & packs on sale, message prices |
| `POST /api/billing/payments` | `{ type: "PLAN", planId, months }` or `{ type: "CREDITS", creditPackId }` → payment + how-to-pay instructions |
| `PUT /api/billing/payments/{id}/submit` | `{ payerPhone, transactionReference }` |
| `POST /api/billing/payments/{id}/cancel` | only while waiting |
| `GET /api/billing/payments` | history (newest first) |
| `GET /api/billing/credit-movements` | credit statement (newest first) |

### Platform admin

| Method & path |
|---|
| `GET /api/platform/plans`, `POST`, `PUT /{id}` |
| `GET /api/platform/credit-packs`, `POST`, `PUT /{id}` |
| `GET /api/platform/message-prices`, `PUT /{channel}` |
| `GET /api/platform/payments?status=` |
| `PUT /api/platform/payments/{id}/confirm`, `PUT /api/platform/payments/{id}/reject` (`{ note }`) |
| `POST /api/platform/companies/{id}/credits` (`{ amount, note }`) |

Company list rows add plan name, plan status, paid-until and credit balance.

### Payment connector

`PaymentProvider { PaymentInstructions instructionsFor(Payment) }`.
`ManualPaymentProvider` reads settings `app.payments.manual.*`
(pay-to name, Lipa Namba / till number, networks text).

## 6. React screens

- **Plan & credits** (menu for OWNER; MANAGER read-only): status banner, usage bars,
  credit balance, plan cards with months picker, credit packs, payment panel
  (instructions → "I have paid" form → waiting), payment history, credit statement.
- Limit errors anywhere show an **Upgrade** link.
- **Platform admin:** "Payments to check" (confirm/reject), "Plans & prices" (plans, packs,
  message prices), companies table with plan/balance columns and "Adjust credits".

## 7. Errors

| Status | Examples |
|---|---|
| 400 | months not 1–12; plan/pack not on sale; missing transaction code; zero adjustment |
| 403 | manager buying; non-admin confirming |
| 409 | plan limit reached; payment not waiting; balance would go below zero |

## 8. Testing

- New companies are on Free; each Free limit is enforced.
- Plan purchase: create → submit → admin confirms → plan active to the right date; months stack.
- Paid-until + 7 days passed → Free limits apply; existing active event's public page and RSVP still work.
- Credits: purchase adds, statement shows running balance, spending cannot go below zero,
  admin adjustments, a payment cannot be confirmed twice.
- Roles and tenant isolation for payments and statements.
