# Check-in at the Door — Design (Sub-project 5 of 7)

Date: 2026-09-22
Status: Approved in brainstorming
Builds on: sub-projects 1–4

## 1. Goal

Staff at the entrance scan each guest's QR code with their phone's camera (or a USB/Bluetooth
barcode scanner, or search by name/phone) and let the right number of people in. Cards covering
several people can be used for part of the group now and the rest later, but never beyond their
seats. Owners and managers see live arrival numbers.

## 2. Decisions made

| Topic | Decision |
|---|---|
| Device | Staff phone browser + camera (no app to install); manual search as backup; hardware scanners type into the page |
| Multi-seat cards | Count people per scan (e.g. 1 of 2 now, the other later); refused beyond the seats |
| Connection | Online now (tiny requests, clear retry on no connection); offline mode can be added later |

## 3. Data model

- `guests` + `people_arrived` (0..seats, default 0), `first_arrived_at`, `last_arrived_at`.
- **`check_ins`** (`@TenantId`): id, company_id, event_id, guest_id, people, method
  (`QR_SCAN` / `MANUAL_SEARCH`), checked_in_by_user_id, created_at, undone_at, undone_by_user_id.

## 4. Rules

- Only **ACTIVE** events (409 otherwise).
- Check in: CHECK_IN_STAFF, MANAGER, OWNER. Undo: MANAGER, OWNER.
- A scan may contain the whole personal link (`…/i/Xk9p2QmT7aBc`) or just the code; the code is
  the part after the last `/`.
- A code of another event or company → result `NOT_FOR_THIS_EVENT`, with no guest details.
- People per check-in: 1..seats left, else 409 ("All 2 already arrived at 16:42", time in the
  event's time zone).
- The guest row is locked during a check-in or undo, so two gates cannot use the same seats at once.
- Undo gives the seats back (once).

## 5. API

| Method & path | Notes |
|---|---|
| `POST /api/events/{id}/check-in/look-up` | `{ scanned }` → `{ status: READY / PARTLY_ARRIVED / ALL_ARRIVED / NOT_FOR_THIS_EVENT, guest }` — nothing saved |
| `POST /api/events/{id}/check-in` | `{ guestId, people, method }` → updated guest arrival |
| `GET /api/events/{id}/check-in/search?q=` | up to 20 guests by name or phone |
| `GET /api/events/{id}/check-in/summary` | people arrived / seats, cards arrived / cards, expected from RSVP, by card type, last 20 check-ins |
| `POST /api/events/{id}/check-ins/{checkInId}/undo` | MANAGER, OWNER |

Guest list rows add `peopleArrived` and `lastArrivedAt`.

## 6. Screens

- **Check-in page** (`/events/{id}/check-in`, phone-first): big "86 of 260 people arrived" counter;
  **Scan** mode with the camera (`@zxing/browser`), beep + vibrate on a read, colour-coded result card
  (green ready / yellow partly arrived / red all arrived or wrong event) with "Let N in" buttons;
  a text box that also receives barcode-scanner input; **Search** mode; recent check-ins with Undo
  (managers/owners). Camera needs HTTPS (or localhost).
- Check-in staff open events straight into the check-in page.
- Event Overview: live "Arrivals" card. Guest list: "Arrived" column.

## 7. Testing

Look-up by link and by bare code; other event/company → `NOT_FOR_THIS_EVENT`; partial arrivals
(1 of 2, then 1, then refused with time); too many people refused; inactive event refused;
roles (check-in staff can check in but not undo; undo restores seats); totals; tenant isolation.
