# Digital Cards — Design (Sub-project 3 of 7)

Date: 2026-09-21
Status: Approved in brainstorming
Builds on: [Multi-tenant core](2026-09-21-multi-tenant-core-design.md), [Events & guests](2026-09-21-events-and-guests-design.md)

## 1. Goal

Each guest gets a **personal card image** (their name, card type and a unique QR code
printed on the vendor's design) and a **personal web page** (card, event details,
map, add-to-calendar and RSVP). Vendors design the card by uploading their own
artwork and placing fields on it, or by choosing a built-in template.

Out of scope: sending (#4), check-in scanning (#5), billing (#6).

## 2. Decisions made

| Topic | Decision |
|---|---|
| What the guest receives | A card image (for WhatsApp) **and** a personal web page (link for SMS, RSVP) |
| How cards are designed | Upload own artwork + drag fields (main path), or 3 built-in templates |
| Card types | One design per event; each card type may optionally have its own background of the same size |
| Where images are made | On the server with Java's drawing tools; QR codes with ZXing; editor preview uses the same code |
| RSVP | Yes/No, number of people (1..seats), optional message; changeable until the RSVP deadline |

## 3. Data model

### `card_designs` (one per event, `@TenantId`)

| Column | Meaning |
|---|---|
| id, company_id, event_id (unique) | |
| kind | `UPLOADED` or `TEMPLATE` (new events start with `TEMPLATE` / `CLASSIC`) |
| template_name | `CLASSIC`, `ELEGANT`, `MODERN` |
| background_file, width, height | uploaded artwork (PNG/JPG ≤ 5 MB) and its size in pixels |
| invitation_text | template wording, max 300 characters |
| version | +1 on every change |

### `card_design_fields` (`@TenantId`) — for uploaded designs

| Column | Meaning |
|---|---|
| id, company_id, design_id | |
| field | `GUEST_NAME`, `CARD_TYPE`, `QR_CODE` (exactly one row each) |
| x, y, width, height | position and size as **percent** of the picture (0–100; box must stay inside) |
| font | `PLAYFAIR`, `PLAYFAIR_BOLD`, `MONTSERRAT`, `MONTSERRAT_BOLD`, `GREAT_VIBES` |
| font_size | in pixels of the original picture (8–400); text shrinks automatically if too long for the box |
| color | `#RRGGBB` |
| align | `LEFT`, `CENTER`, `RIGHT` |
| visible | hide a field (e.g. card type) |

The QR code is drawn as a square: the smaller of the box's width and height.

Fonts are bundled `.ttf` files (SIL Open Font License): Playfair Display, Montserrat, Great Vibes.

### Changes to existing tables

- `card_types.background_file` (optional): a special background for this type.
  Must have exactly the same pixel size as the main artwork. Only for `UPLOADED` designs.
- `guests`:
  - `invitation_code` — 12 random letters/digits, unique; created automatically for
    every new guest, and filled in at startup for guests created before this part.
  - `rsvp_status` — `NO_REPLY` (default), `ATTENDING`, `NOT_ATTENDING`
  - `rsvp_people` — 1..seats when attending, otherwise empty
  - `rsvp_message` — optional, max 300
  - `rsvp_answered_at`

### Built-in templates

Portrait 1080 × 1350 px (fits WhatsApp well). Drawn by code using the company's
brand colours and logo and the event's details:

| Template | Look |
|---|---|
| `CLASSIC` | white card, thin frame in the main colour, serif lettering |
| `ELEGANT` | second colour background, script lettering for names |
| `MODERN` | main-colour block at the top, clean sans-serif lettering |

Each shows: logo (if any), "You are invited", host names + invitation text,
the guest's name, event name, date & time, venue, card type and the QR code
with "Show this code at the entrance".

## 4. Links, QR codes and card images

- **Personal link:** `https://<site>/i/<invitationCode>`
  - `<site>` = the company's verified custom domain, otherwise the website address (`app.frontend-url`).
- **QR code** contains the personal link (a phone camera opens the page; the door scanner in #5 reads the code from it).
- **Card images** are made when first requested and saved in a private folder
  (`app.card-cache-folder`, not publicly served). The file name includes a
  fingerprint of everything shown on the card (design version, guest name, card type,
  event and company details), so any change automatically produces a fresh image.
- **Custom domains:** the domain page adds a second instruction —
  `CNAME <domain> → app.custom-domain-target`. HTTPS certificates for vendor domains
  are a hosting task (e.g. Caddy on-demand TLS), documented in the README.

## 5. API

### Vendor (logged in; view: all company roles, change: OWNER / MANAGER)

| Method & path | Notes |
|---|---|
| `GET /api/events/{id}/card-design` | the design (created as Classic template if the event has none yet) |
| `PUT /api/events/{id}/card-design` | `{ kind, templateName, invitationText, fields: [...] }` |
| `POST /api/events/{id}/card-design/background` | multipart `file`; switches the design to `UPLOADED`; first upload places default field boxes |
| `POST /api/events/{id}/card-types/{typeId}/background` | multipart `file`; same size as the main artwork |
| `DELETE /api/events/{id}/card-types/{typeId}/background` | back to the main artwork |
| `GET /api/events/{id}/card-design/preview.png?cardTypeId=&template=` | sample card ("Mr & Mrs Sample"); `template` previews a template without saving |
| `GET /api/events/{id}/guests/{guestId}/card.png` | a real guest's card |

Guest list additions: each guest has `invitationCode`, `invitationLink`, `rsvpStatus`,
`rsvpPeople`, `rsvpMessage`; filter `?rsvp=ATTENDING|NOT_ATTENDING|NO_REPLY`.
Event details add `rsvp: { attendingCards, attendingPeople, notAttendingCards, noReplyCards }`.

### Public (no login — the invitation code is the key)

| Method & path | Notes |
|---|---|
| `GET /api/public/invitations/{code}` | guest name, card type, seats, RSVP answer, event details, company name/logo/colours, whether RSVP is still open |
| `GET /api/public/invitations/{code}/card.png` | the guest's card image |
| `POST /api/public/invitations/{code}/rsvp` | `{ attending, people, message }` |

- Only **ACTIVE** events: otherwise 404 "This invitation is not available".
- RSVP is refused (409) after the RSVP deadline (end of that day in the event's time zone).
- More than 30 unknown codes per minute from one address → 429 "Too many attempts".

## 6. React screens

- **Card design tab:** pick *Upload your own design* or a template (with live
  thumbnails). Uploaded design: artwork with three draggable/resizable boxes
  (Guest name, Card type, QR code) and a side panel for font, size, colour,
  alignment, visible. Template: edit wording. Per-card-type backgrounds.
  **Preview** shows the real server-drawn image for any card type.
- **Guest list:** RSVP column + filter; per guest "Copy link", "Open card".
- **Overview:** RSVP totals.
- **Custom domain page:** CNAME instruction.
- **Public invitation page** `/i/:code` — mobile-first, company colours: card image,
  details, map button, "Add to calendar" (.ics), RSVP form, "Invitation by <company>".

## 7. Errors

| Status | Examples |
|---|---|
| 400 | artwork not PNG/JPG or > 5 MB; card-type background of a different size; box outside the picture; unknown font; people > seats |
| 404 | unknown code; event not active; other company's design |
| 409 | RSVP after deadline; changing the design of a finished/cancelled event; card-type background on a template design |
| 429 | too many unknown invitation codes |

## 8. Testing

- Card images: correct size; the QR decodes back to the guest's link; a card type's own background is used.
- Image reuse: same image when nothing changed; new image after design or guest-name change.
- Templates render for all three styles.
- Public page + RSVP only for ACTIVE events; seat limit and deadline enforced; RSVP counted in event totals.
- Guests get unique codes (including by import); older guests are filled in at startup.
- Tenant isolation: design endpoints 404 for other companies; a code only shows its own guest.
- Links use the verified custom domain.
