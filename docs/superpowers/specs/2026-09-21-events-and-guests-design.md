# Events & Guests — Design (Sub-project 2 of 7)

Date: 2026-09-21
Status: Approved in brainstorming
Builds on: [Multi-tenant core](2026-09-21-multi-tenant-core-design.md)

## 1. Goal

A vendor (OWNER or MANAGER) can create events, define card types (Single, Double,
VIP…), and build the guest list — one guest entry per card — by hand or by
uploading an Excel/CSV file with a preview before importing.

Out of scope here (later sub-projects): card design (#3), sending (#4),
check-in (#5), billing/limits (#6), contributions / michango (separate later part),
RSVP answers (#3).

## 2. Decisions made

| Topic | Decision |
|---|---|
| What a guest entry is | One entry = one card (e.g. "Mr & Mrs Juma"), with a card type giving the seat count |
| Card types | Custom per event; every new event starts with Single (1 seat) and Double (2 seats) |
| Contributions (michango) | Not in this part |
| Upload flow | Two calls, nothing stored in between: "check" returns a preview, "import" re-checks and saves the good rows |
| Files | `.xlsx` (Apache POI) or `.csv` (Apache Commons CSV); max 5,000 rows, 5 MB |

## 3. Data model

All three tables are tenant-filtered with `@TenantId company_id`, exactly like `users`.

### `events`

| Column | Rule / meaning |
|---|---|
| id | UUID |
| company_id | `@TenantId` |
| name | required, max 150 — "Asha & Baraka's Wedding" |
| event_type | `WEDDING`, `SEND_OFF`, `KITCHEN_PARTY`, `BIRTHDAY`, `GRADUATION`, `CONFERENCE`, `OTHER` |
| host_names | optional, max 150 — "Mr & Mrs Salim" |
| starts_at | required; local date & time of the event |
| ends_at | optional; must be after `starts_at` |
| time_zone | copied from the company when the event is created, e.g. `Africa/Dar_es_Salaam` |
| venue_name | required, max 150 |
| venue_address | optional, max 255 |
| map_link | optional, must start with `http://` or `https://` |
| dress_code | optional, max 100 |
| extra_info | optional, max 2,000 |
| contact_phone | optional, normalised like guest phones |
| rsvp_deadline | optional date |
| status | `DRAFT` (default), `ACTIVE`, `FINISHED`, `CANCELLED` |
| created_by_user_id, created_at, updated_at | |

Dates are stored as the event's local wall-clock time plus its time zone, so
"4:00 PM in Dar es Salaam" is always shown as 4:00 PM.

**Status changes allowed:**

| From | To |
|---|---|
| DRAFT | ACTIVE, CANCELLED |
| ACTIVE | FINISHED, CANCELLED |
| FINISHED, CANCELLED | — (final) |

`FINISHED` and `CANCELLED` events are **read-only** (event, card types and guests).

### `card_types`

| Column | Rule / meaning |
|---|---|
| id, company_id (`@TenantId`), event_id | |
| name | required, max 40, unique within the event (ignoring letter case) |
| seats | 1–50 |
| sort_order | display order; new types go last |

Rules: an event always keeps at least one card type; a card type used by any
guest cannot be deleted (409 — move the guests first).

### `guests` (one row per card)

| Column | Rule / meaning |
|---|---|
| id, company_id (`@TenantId`), event_id | |
| name_on_card | required, max 150 |
| phone | required, stored in international format `+255712345678`; unique within the event |
| card_type_id | required, must belong to the same event |
| group_name | optional, max 60 — "Bride's side" |
| notes | optional, max 500 |
| created_at, updated_at | |

Seats per guest come from the card type. Totals (cards, seats, per card type,
per group) are calculated when asked for, never stored.

### Phone number clean-up

Numbers are turned into international format using the **company's country**:

| Typed | Company in TZ becomes |
|---|---|
| `0712 345 678` | `+255712345678` |
| `712345678` (Excel often drops the leading 0) | `+255712345678` |
| `255712345678` | `+255712345678` |
| `+255 712-345-678` / `00255712345678` | `+255712345678` |

After clean-up the number must be `+` followed by 8–15 digits, otherwise it is invalid.

### Who can do what

| Action | OWNER | MANAGER | CHECK_IN_STAFF |
|---|---|---|---|
| View events, card types, guests | ✔ | ✔ | ✔ (read-only) |
| Create/edit events, card types, guests; import | ✔ | ✔ | |
| Change event status | ✔ | ✔ | |
| Delete an event (DRAFT only) | ✔ | | |

Deleting an event also deletes its card types and guests. Only `DRAFT` events
can be deleted; others must be cancelled.

## 4. API

### Events

| Method & path | Notes |
|---|---|
| `GET /api/events?status=&search=&page=` | 20 per page, soonest-first by start date; each row has card & seat totals |
| `POST /api/events` | creates as `DRAFT` + Single & Double card types |
| `GET /api/events/{eventId}` | event + card types + totals by card type and by group |
| `PUT /api/events/{eventId}` | edit details |
| `PUT /api/events/{eventId}/status` | body `{ "status": "ACTIVE" }` |
| `DELETE /api/events/{eventId}` | OWNER, DRAFT only |

### Card types

| Method & path |
|---|
| `POST /api/events/{eventId}/card-types` body `{ name, seats }` |
| `PUT /api/events/{eventId}/card-types/{cardTypeId}` |
| `DELETE /api/events/{eventId}/card-types/{cardTypeId}` |

### Guests

| Method & path | Notes |
|---|---|
| `GET /api/events/{eventId}/guests?search=&cardTypeId=&group=&page=` | 50 per page, sorted by name; search matches name or phone |
| `POST /api/events/{eventId}/guests` | body `{ nameOnCard, phone, cardTypeId, groupName, notes }` |
| `PUT /api/events/{eventId}/guests/{guestId}` | |
| `DELETE /api/events/{eventId}/guests/{guestId}` | |
| `POST /api/events/{eventId}/guests/import/check` (multipart `file`) | preview — saves nothing |
| `POST /api/events/{eventId}/guests/import` (multipart `file`) | saves the good rows |
| `GET /api/events/{eventId}/guests/import/template` | `.xlsx` template with this event's card types |

### Guest list files

- First row = column headings. Headings are matched ignoring case, spaces and
  punctuation, in English or Swahili:

| Column | Accepted headings | Required |
|---|---|---|
| Name | name, name on card, guest, guest name, full name, jina | yes |
| Phone | phone, phone number, mobile, whatsapp, telephone, simu, namba, namba ya simu | yes |
| Card type | card type, type, card, category, aina, aina ya kadi | no — blank means the event's first card type |
| Group | group, side, kundi, upande | no |
| Notes | notes, note, comments, maelezo | no |

- Completely empty rows are ignored.
- The whole file is refused (400) if it is not `.xlsx`/`.csv`, cannot be read,
  has no Name or Phone column, or has more than 5,000 guest rows.
- Otherwise each row is one of:
  - **ready** — will be imported;
  - **problem** — e.g. "Row 14: phone number is not valid", "Row 20: card type 'VVIP' does not exist";
  - **duplicate** — phone already in this event's guest list, or earlier in the same file (skipped).

Preview answer:
```json
{
  "readyCount": 148,
  "readyExamples": [ { "row": 2, "nameOnCard": "...", "phone": "+255...", "cardType": "Double", "groupName": null } ],
  "problems": [ { "row": 14, "message": "Phone number is not valid" } ],
  "duplicates": [ { "row": 30, "nameOnCard": "...", "phone": "+255..." } ]
}
```
(`readyExamples` = first 10 ready rows.) The import answer is
`{ "importedCount": 148, "problems": [...], "duplicates": [...] }`.

Uploads: the global upload limit becomes 5 MB; logos keep their own 2 MB limit.

## 5. React screens

- **Events** (new first menu item; company users land here after login):
  list of event cards — name, date, venue, status badge, "150 cards · 260 seats";
  status filter + search; **New event** button (OWNER/MANAGER).
- **Event form** (create / edit).
- **Event page** with tabs:
  - **Overview** — details, status actions (Activate / Finish / Cancel / Delete draft),
    totals by card type and by group.
  - **Guests** — search + filters, paged table, add/edit side panel,
    **Upload guest list**: download template → choose file → preview → Import.
  - **Card types** — add, rename, change seats, remove.
  - **Card design** — placeholder: "Coming in the next part".
- Read-only views for CHECK_IN_STAFF and for FINISHED/CANCELLED events.

## 6. Errors

Same `{ message, field }` shape as sub-project 1.

| Status | Examples |
|---|---|
| 400 | end before start; invalid phone; bad map link; unreadable / wrong file type; missing Name/Phone column; too many rows |
| 403 | check-in staff editing; manager deleting an event |
| 404 | event/guest/card type not found or belongs to another company; card type from another event |
| 409 | duplicate phone in event; duplicate card type name; deleting a used or the last card type; editing a FINISHED/CANCELLED event; deleting a non-DRAFT event; disallowed status change |

## 7. Testing

Same setup as sub-project 1 (PostgreSQL in Docker, H2 fallback).

- Tenant isolation: company A cannot list, read, edit, import into or delete
  company B's events, card types or guests.
- New event gets Single (1) and Double (2).
- Phone clean-up cases from the table above; duplicate phone in an event → 409;
  the same phone in two different events is fine.
- Import check: counts ready/problem/duplicate rows correctly and saves nothing.
- Import: saves only ready rows; tested with an `.xlsx` and a `.csv` file,
  including Swahili headings and a numeric phone cell without its leading 0.
- File refused: wrong type, missing Phone column, too many rows.
- Card types: cannot delete a used or the last one; totals (cards/seats) correct.
- Roles: check-in staff read-only; only owner deletes; only DRAFT deletable.
- FINISHED/CANCELLED events are read-only; status change rules enforced.
