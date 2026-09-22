# Sending Cards (WhatsApp & SMS) — Design (Sub-project 4 of 7)

Date: 2026-09-22
Status: Approved in brainstorming
Builds on: sub-projects 1, 2, 3 and 6 (credits)

## 1. Goal

Vendors send each guest their card: over **WhatsApp** (card image + text with the
personal link) or **SMS** (text with the personal link), or WhatsApp with automatic
SMS fallback. Sending happens in batches with a cost preview, or one guest at a
time. Each message costs credits; failed messages are refunded automatically.

## 2. Decisions made

| Topic | Decision |
|---|---|
| Services | WhatsApp: Meta WhatsApp Cloud API. SMS: Beem Africa. Plus a **pretend** connector (default) for development and tests |
| Sender | One platform WhatsApp number and SMS sender name; the admin can set a company's own SMS sender name once registered |
| How | Batch sending (who + how + cost preview) **and** Send/Resend per guest |
| Wording | Standard wording in Swahili or English (per event). WhatsApp uses approved Meta templates; SMS text is editable per event with placeholders |
| Queue | A `messages` table in the database + a background worker (survives restarts, controlled pace) |

## 3. Data model

**`messages`** (`@TenantId`): id, company_id, event_id, guest_id, batch_id (empty for single sends),
channel (`WHATSAPP`/`SMS`), fallback_to_sms, to_phone, text (final SMS text, or the WhatsApp
wording for display), credits_charged, credits_refunded, status
(`QUEUED` → `SENDING` → `SENT` → `DELIVERED` → `READ`, or `FAILED`), provider_message_id,
failure_reason, attempts, next_attempt_at, queued_at, sent_at, delivered_at, read_at.

**`send_batches`** (`@TenantId`): id, company_id, event_id, channel choice, description of who,
message_count, credits_charged, created_by_user_id, created_at.

**Changes:** `events.message_language` (`SW` default / `EN`), `events.sms_text` (empty = standard);
`companies.sms_sender_name` (empty = platform sender; 3–11 letters/digits, set by the admin).

A guest's **card status** = the status of their latest message (or "not sent").

## 4. Wording

Placeholders: `{name}` `{company}` `{event}` `{date}` `{venue}` `{link}`. Unknown placeholders are refused.

| | Standard wording |
|---|---|
| SW | `Habari {name}! {company} inakualika {event}, {date}, {venue}. Kadi & RSVP: {link}` |
| EN | `Hi {name}! {company} invites you to {event}, {date}, {venue}. Card & RSVP: {link}` |

WhatsApp sends the approved template for the language (`app.messaging.meta.template-sw/-en`)
with the card image as header and body values: name, company, event, date, link.

**SMS parts:** plain letters: 160 for one part, 153 per part after that. Text with other
characters (e.g. emoji): 70, then 67 per part. SMS text may be at most 5 parts.

## 5. Credits

- Credits are taken **when messages are queued**, as one statement line per batch
  (e.g. "Cards for Asha & Baraka's Wedding: 148 WhatsApp"). Not enough credits → nothing is queued (409).
- WhatsApp = its message price; SMS = price × parts (per guest, since names differ in length).
- A message that finally fails is **refunded** (`MESSAGE_REFUND`, once only).
- WhatsApp→SMS fallback: the failed WhatsApp is refunded and the SMS is charged when queued
  (if credits are short, the fallback is skipped and the reason recorded).

## 6. Sending

**Connectors:** one `MessageSender` interface (one per channel) → result (provider id, delivered already?)
or a failure marked *temporary* (retry) or *permanent*.
Choice in settings: `app.messaging.whatsapp = pretend | meta`, `app.messaging.sms = pretend | beem`.
The pretend connector marks messages delivered at once. Phone numbers ending in `0000` fail
permanently on WhatsApp only ("not on WhatsApp"), `1111` fail permanently on SMS only, `9999` fail
temporarily on both — handy for demos and tests.

**Worker** (every 2 seconds, up to 20 messages, `app.messaging.worker-enabled`):
skips/fails messages whose company is suspended or not allowed to send, or whose event is no
longer ACTIVE. Temporary failure → retry after 1, 5, 15 minutes, then fail. Permanent → fail now.
Statuses only move forward (a late "delivered" never overwrites "read").

**Delivery reports:**
- `GET/POST /api/public/webhooks/whatsapp` — Meta verify token; POST checked with
  `X-Hub-Signature-256` (HMAC-SHA256 of the body with the app secret).
- `POST /api/public/webhooks/beem?token=...` — shared secret token in the callback address;
  matches `request_id`.

## 7. API (OWNER, MANAGER)

| Method & path | Notes |
|---|---|
| `POST /api/events/{id}/sending/preview` | `{ who, cardTypeId, group, rsvp, guestIds, channel }` → count, credits needed, balance, skipped (with reason), sample SMS + parts |
| `POST /api/events/{id}/sending` | same body → batch |
| `POST /api/events/{id}/guests/{guestId}/send` | `{ channel }` |
| `GET /api/events/{id}/sending` | totals per status, recent batches, recent failures |
| `GET /api/events/{id}/guests/{guestId}/messages` | one guest's messages |
| `PUT /api/events/{id}/message-settings` | `{ language, smsText }` |

`who`: `ALL`, `NOT_SENT`, `FAILED`, `FILTER` (card type / group / RSVP), `SELECTED` (guest ids).
`channel`: `WHATSAPP`, `SMS`, `WHATSAPP_THEN_SMS`.
Guests with a message already queued/sending are skipped.
Rules: event ACTIVE (409), company allowed to send (403), at least one guest (400), enough credits (409).

Admin: `PUT /api/platform/companies/{id}/sms-sender` `{ smsSenderName }` (empty clears it).

## 8. Screens

- **Send cards** tab: banner when sending isn't possible; who; how; language + WhatsApp wording
  (read-only) + editable SMS text with live letter/part counter; cost preview; send; live totals
  (refresh every 3 s while messages are queued); recent failures; "Resend failed".
- **Guest list:** card status column; guest panel: Send/Resend with channel + message history.
- **Admin companies:** SMS sender name.

## 9. Testing

Pretend connector (+ its failing numbers): preview counts/credits (WhatsApp, multi-part SMS,
fallback); refusal with too few credits; worker sends and statuses progress; temporary retry
then fail + refund; permanent fail + refund; WhatsApp→SMS fallback charging; locked company /
inactive event / check-in staff; signed WhatsApp webhook accepted, bad signature refused; Beem
report; placeholders in both languages, unknown placeholder refused; tenant isolation.
