# PROJECT_INFO — team-lovelace-babbage (SEP)

Factual briefing generated from the repository contents. Everything below is verifiable in the
code unless explicitly marked `[VERIFY]`.

---

## 1. What the project is

A web-based travel and calendar management platform ("SEP"), built by a university team project
group. Users keep a personal calendar of events, group those events into trips, track the costs and
budget of each trip, and coordinate all of it with friends through invitations and privacy-gated
sharing.

The problem it solves: trip planning is normally scattered across mail confirmations, spreadsheets
and group chats. This app pulls bookings out of email (file upload, IMAP, or a connected Gmail
account — parsed with AI), puts them on a shared calendar, attaches expenses and a budget to each
trip, and lets friends be invited to trips and single events with per-friend privacy control.

Target users: small groups of friends planning shared trips; the seeded test users and German-language
UI strings indicate it is a coursework/demo product, not a commercial one.

---

## 2. Tech stack

| Technology | Version | Role |
|---|---|---|
| Java | 21 (Gradle toolchain) | Backend language |
| Spring Boot | 3.5.6 | Backend framework (web, data-jpa, security, mail, websocket) |
| Gradle | wrapper | Backend build (`bootJar`, `test`, `spotlessCheck`) |
| Spotless + google-java-format | plugin 8.4.0 | Backend code formatting; `check` depends on `spotlessCheck` |
| Spring Data JPA / Hibernate | via Boot 3.5.6 | Persistence; `ddl-auto=update` (schema generated from entities, **no migrations**) |
| PostgreSQL | 18 (Docker image) | Database, DB name `sep`, user `sep` |
| Spring Security | via Boot | Stateless auth, BCrypt password hashing, CORS |
| JWT | **hand-rolled** (HMAC-SHA256, `javax.crypto.Mac`) | Bearer tokens; no JWT library |
| spring-dotenv (`me.paulschwarz`) | 4.0.0 | Loads root `.env` into Spring properties |
| google-genai (`com.google.genai`) | 1.53.0 | Gemini SDK — event extraction/detection from mail text |
| Gemini REST API (`generativelanguage.googleapis.com`) | v1beta, model `gemini-2.5-flash` | Event *recommendations* (called via Spring `RestClient`, not the SDK) |
| google-api-client | 2.7.2 | Google ID-token verification + Gmail OAuth code/refresh exchange |
| google-api-services-gmail | v1-rev20260525-2.0.0 | Gmail REST client (read-only) |
| Jakarta Mail (via spring-boot-starter-mail) | via Boot | IMAP reading and RFC822/MIME parsing; SMTP sending |
| Spring WebSocket + STOMP | via Boot | Real-time invite/friend notifications |
| Angular | 21.2.x | Frontend SPA, standalone components + signals |
| TypeScript | 5.9.x | Frontend language |
| Tailwind CSS | v4 (via `@tailwindcss/postcss`) | Styling |
| Angular Material + CDK | 20.2.x | Dialogs, menus, form controls |
| FullCalendar | 6.1.20 | Calendar view (daygrid/timegrid/list/interaction) |
| ApexCharts (`ng-apexcharts`) + Chart.js | 5.12 / 4.5 | Expense charts |
| `@stomp/stompjs` + `sockjs-client` | 7.3.0 / 1.6.1 | WebSocket client |
| Karma + Jasmine | 6.4 / 5.9 | Frontend unit tests (`npm test`) |
| Playwright | 1.59.1 | Frontend end-to-end tests |
| ESLint + angular-eslint | 10.x / 21.4 | Frontend linting (`npm run lint`) |
| Nginx | alpine | Serves the built frontend, reverse-proxies `/api/` to the backend |
| Docker / Docker Compose | — | Local + CI packaging (`compose.yaml`) |
| GitLab CI | `.gitlab-ci.yml` | Lint → build → test pipeline |

---

## 3. Architecture

Two independent applications plus a database, wired together by Docker Compose.

**Request flow (normal REST call):**

1. The Angular SPA (`frontend/`) issues an HTTP call to a relative `/api/...` path from an
   `@Injectable({providedIn:'root'})` service (`src/app/services/*.ts`,
   `src/app/account-management/account-management.service.ts`, `src/app/TripManagement/services/trip.service.ts`).
2. `authInterceptor` (`src/app/account-management/auth.interceptor.ts`) reads the JWT from
   `localStorage['sepToken']` and adds `Authorization: Bearer <token>` to every outgoing request.
3. The request reaches the backend. In development, the Angular dev server proxies `/api` to
   `http://localhost:8082` (`frontend/proxy.conf.json`). In Docker, Nginx proxies `/api/` to
   `http://backend:8080/api/` (`frontend/nginx.conf`, with 300 s proxy timeouts because AI-backed
   imports are slow).
4. In Spring Security, `JwtAuthFilter` (registered before `UsernamePasswordAuthenticationFilter`)
   validates the token's HMAC signature and expiry and puts a
   `UsernamePasswordAuthenticationToken` carrying only the **username** into the `SecurityContext`.
   `SecurityConfig` permits `/api/account/**`, `/ws/**` and all `OPTIONS`; everything else requires
   authentication.
5. A `@RestController` (e.g. `TripController`) receives the request. It resolves the `Authentication`
   into a `User` row — either via `CurrentUserService.requireUser(...)` or by calling
   `UserRepository.findByNameIgnoreCaseOrEmailIgnoreCase(...)` directly — and delegates to a
   `@Service`.
6. The `@Service` (e.g. `TripService`) holds the business rules and authorization checks
   (membership, friendship, invite state) and calls Spring Data JPA `*Repository` interfaces.
7. Repositories (`com.sep.sep_backend.repos`) issue SQL against PostgreSQL through Hibernate.
   Several use explicit `@Query` JPQL (conflict detection, per-user trip/event lookups).
8. The response is mapped to a DTO (record or POJO) and serialized to JSON. Exceptions are turned
   into `{"message": "..."}` bodies by `GlobalExceptionHandler` (`@RestControllerAdvice`).

**Real-time flow:** `WebSocketConfig` exposes a raw STOMP endpoint at `/ws`. The frontend
`WebSocketService` connects with `ws://localhost:8082/ws?token=<jwt>`; `JwtHandshakeInterceptor`
validates that query-parameter token during the HTTP handshake and rejects the handshake if it is
missing/invalid. `UserHandshakeHandler` binds the username as the STOMP `Principal`, so the server
can push to `/user/queue/...`. A simple in-memory broker serves `/topic` and `/queue`.

**External services:**

- **Google Gemini** — two independent integration paths:
  - `EventService` uses the `google-genai` SDK (`Client.builder().apiKey(...)`) to extract or detect
    an event inside a mail body.
  - `GeminiEventSearchService` calls the Gemini REST API directly with Spring `RestClient`
    (`POST /v1beta/models/{model}:generateContent`, header `x-goog-api-key`) for event
    recommendations by location.
- **Google Identity Services** — the frontend loads `https://accounts.google.com/gsi/client` and
  obtains an **ID token**; the backend verifies it with `GoogleIdTokenVerifier` against the
  configured client id (`GoogleTokenVerifier`). No client secret is needed for login.
- **Google OAuth 2.0 authorization-code flow** — used *only* for Gmail import
  (`GmailOAuthService`); this one **does** need `GOOGLE_OAUTH_CLIENT_SECRET`.
- **Gmail REST API** — read-only message list/get (`GmailClient`).
- **IMAP** — direct `imaps` connection to a user-supplied mailbox (`EventService.getImapMailPreviews`,
  `parseImapMessage`).
- **SMTP** — Spring Mail (`smtp.gmail.com:587` by default) for verification codes, reset codes and
  invite/friend/trip notifications (`EmailNotificationService`).

---

## 4. Feature list

### 4.1 Account management (register / login / verify / reset)
- **What:** username+email registration with email verification, login, forgotten-password recovery
  by security question ("What is your favorite color?") and/or a mailed 6-digit code.
- **How:** `AccountManagementService` validates the username (`^[A-Za-z][A-Za-z0-9_]{2,}$`), the
  password (`≥9 chars, ≥1 uppercase, ≥1 digit, ≥1 symbol`) and email shape; hashes password and
  favorite-colour answer with BCrypt; stores a `SecureRandom` 6-digit `verificationCode` valid for
  15 minutes. Login is refused until `emailVerified` is true. If SMTP fails, the code is returned
  **in the HTTP response body** as a fallback so local setups without a mail server still work.
- **Files:** `backend/.../accountmanagement/AccountManagementController.java`,
  `AccountManagementService.java`, `*RequestDTO.java`;
  `frontend/src/app/account-management/` (component, service, guard, interceptor).

### 4.2 Google login (OAuth / OIDC ID-token flow)
- **What:** "Sign in with Google" button; first-time Google users are auto-provisioned.
- **How:** Google Identity Services in the browser returns an **ID token**;
  `POST /api/account/oauth/google` verifies it with `GoogleIdTokenVerifier` (audience = configured
  client id), requires `email_verified`, then finds the user by email or creates one with
  `provider="GOOGLE"`, `emailVerified=true`, a generated unique username derived from the email
  local part, and an **unusable random BCrypt hash** as the password (so the NOT NULL column is
  satisfied without a guessable secret).
- **Files:** `accountmanagement/GoogleTokenVerifier.java`, `AccountManagementService.loginWithGoogle`,
  `frontend/src/app/profile-management/google-verification.ts`, `frontend/public/config.js`.

### 4.3 Profile management (view / change password / delete account)
- **What:** account page showing username, name, email, provider and verification state; password
  change; permanent account deletion.
- **How:** both password change and deletion require proof of ownership — either the mailed one-time
  code **or** a fresh Google ID token whose email matches the account (`verifyEmailOwnership`).
  Deletion is a hand-written cascade (there is no DB-level cascade): unlink symmetric friendships,
  delete friend requests on both sides, re-anchor invites this user sent onto the invitee, delete
  the user's `user_events` rows, delete personal (trip-less) events left with no participants, null
  out `invited_by_id` on other members' trips, delete the user's trip memberships and expenses, then
  delete any trip left with zero members together with its events and expenses. `userRepository.flush()`
  is called so a constraint failure aborts before the "account deleted" email goes out.
- **Files:** `profilemanagement/ProfileManagementService.java`, `ProfileManagementController.java`;
  `frontend/src/app/profile-management/` (profile-menu, profile-details-dialog,
  change-password-dialog, delete-account-dialog).

### 4.4 Calendar & event management
- **What:** a FullCalendar view of every event the user takes part in; create, edit, delete events;
  overlap ("conflict") detection.
- **How:** `GET /api/event/pull/all_events` returns `EventCalendarDTO`s from
  `UserEventRepository.findAllByUserId`, a JPQL query returning events the user **accepted directly**
  *plus* all events of trips where their membership is `ACCEPTED`. Conflict detection
  (`findConflictingEventsForUser`) returns events with `startTime < :end AND endTime > :start` and
  status in `(ACCEPTED, PENDING)`. An event with no explicit end is defaulted to `start + 1 day`
  (`resolveEndTime`). Events attached to a trip are validated to fall inside the trip's date range
  (`EventOutsideTripException`).
- **Files:** `eventHandling/EventController.java`, `EventService.java`, `entities/Event.java`,
  `repos/EventRepository.java`, `repos/UserEventRepository.java`;
  `frontend/src/app/components/calendar/`, `components/event-dialog/`, `components/conflict-dialog/`,
  `src/event-adding-manual/`.

### 4.5 Event import from email — three sources, one parser
- **What:** turn a booking/confirmation email into a calendar event. Three entry points:
  1. **Upload an `.eml` file** — `POST /api/event/import/parse` (multipart).
  2. **IMAP** — user supplies host/port/username/password; the app lists the newest messages and
     parses a chosen one by UID (`POST /api/event/import/imap/messages`, `.../imap/parse`).
  3. **Gmail OAuth** — see 4.6.
- **How (the shared pipeline, `EventService.ParseMailFile`):** the RFC822 message is parsed with
  Jakarta Mail into a `MimeMessage`, then a **two-stage extraction**:
  - **Stage 1 — JSON-LD.** Walk all MIME parts for `application/ld+json` blocks; accept a block whose
    `@context` mentions schema.org (string, array or object form) and whose `@type` is an `Event`
    (also handling `schema:Event`, full URLs, arrays and `@graph`-nested nodes). Structured data is
    preferred because it is exact.
  - **Stage 2 — Gemini fallback.** If no JSON-LD event is found, the plain-text body plus the subject
    is sent to Gemini with `responseMimeType=application/json` and `temperature=0.1`, asking for
    `{name, kosten, startDate, endDate}`. The **mail's sent date (plus weekday)** is injected into the
    prompt as the reference date so relative phrases ("morgen", "nächsten Montag") resolve correctly
    rather than against today.
  - Date parsing is lenient (`tryParseDatetime`): accepts offset/zoned, `yyyy-MM-ddTHH:mm:ss`,
    the same without seconds, and date-only (mapped to start of day).
- **Files:** `eventHandling/EventService.java` (`ParseMailFile`, `extractJsonLd`, `isSchemaOrg`,
  `isSchemaEvent`, `parseTextWithGemini`), `ImapMailboxRequestDTO`, `ImapMailSelectionRequestDTO`,
  `ImapMailPreviewDTO`; `frontend/src/app/components/event-transfer-dialog/`.

### 4.6 Gmail import (OAuth connect + multi-select auto-import)
- **What:** connect a Gmail account once, list recent messages, multi-select them, and import only
  the ones that actually contain an event.
- **How:**
  - **Connect:** the frontend builds the Google consent URL with `scope=gmail.readonly`,
    `access_type=offline`, `prompt=select_account consent`, and a random `state` stored in
    `sessionStorage`. Google redirects back to the `/gmail-import` route; the component verifies the
    `state` (CSRF guard), then posts the authorization `code` to `POST /api/gmail/connect`.
    `GmailOAuthService.exchangeCode` swaps it for an access + **refresh** token (and errors clearly if
    Google returned no refresh token). The refresh token is **encrypted at rest** with AES-256-GCM
    (`TokenCipher`, Spring Security `Encryptors.delux`, per-message random IV) and stored in
    `gmail_accounts`. Access tokens are never persisted — they are derived on demand from the
    refresh token for each call.
  - **Auto-import:** `POST /api/gmail/import/auto` takes the selected message ids, fetches each raw
    RFC822 message, and runs `parseMailFileIfEvent`, which asks Gemini a **classification** question
    ("does this mail describe a real appointment? Ads/newsletters/invoices are NOT events") before
    extracting. Messages with no event, no parseable start date, or only the placeholder name
    `"Unbekanntes Event"` are silently skipped. Messages whose detection **failed** (API error, quota)
    are counted separately and reported back as `failed`, so a failure is never silently rendered as
    "no event". Fetch+classify runs on a fixed thread pool of 3
    (`AUTO_IMPORT_PARALLELISM`), and the Gemini detection call retries twice with linear backoff
    (`400 ms * attempt`).
  - **Disconnect:** `DELETE /api/gmail/disconnect` deletes the stored row so a different account can
    be connected.
- **Files:** `gmailimport/GmailImportController.java`, `GmailImportService.java`, `GmailOAuthService.java`,
  `GmailClient.java`, `TokenCipher.java`, `dto/*`; `entities/GmailAccount.java`;
  `frontend/src/app/services/gmail.service.ts`, `frontend/src/app/components/gmail-import/`.

### 4.7 Trip management
- **What:** create/list/open/delete trips (name + optional start/end date); trips group events and
  expenses.
- **How:** `TripService` centralizes authorization: `requireMember(tripId, userId)` demands an
  **`ACCEPTED`** `user_trips` row, so guessing a trip id gives 403 and a merely *pending* invite does
  not grant read access. `createTrip` rejects a blank name and an end-before-start range, resolves the
  creator **before** saving so a bad user id cannot leave an orphaned trip, and makes the creator the
  first accepted member. `deleteTrip` is `@Transactional` and deletes, in order, the trip's
  `user_events`, events, expenses, memberships, then the trip.
- **Files:** `tripManagement/controllers/TripController.java`, `services/TripService.java`,
  `entities/Trip.java`, `entities/UserTrip.java` (composite key `UserTripId`);
  `frontend/src/app/TripManagement/pages/trip-list/`, `pages/trip-detail/`, `services/trip.service.ts`.

### 4.8 Trip events & categories
- **What:** add/list/delete events inside a trip, move an event to a different trip, free-text
  categories.
- **How:** every operation goes through `TripService.requireMember`. `CategoryService.getOrCreate`
  resolves a typed category name case-insensitively and trimmed, creating it only if it does not
  exist — so "Food", "food " and "FOOD" all map to one row. `GET /api/categories/recent` returns the
  10 highest ids (`findTop10ByOrderByIdDesc`) as "recent", avoiding a timestamp column. Five base
  categories (FOOD, TRANSPORT, HOTEL, LEISURE, OTHER) are seeded from `data.sql`.
- **Files:** `tripManagement/controllers/TripEventController.java`, `CategoryController.java`,
  `services/TripEventService.java`, `services/CategoryService.java`,
  `backend/src/main/resources/data.sql`.

### 4.9 Expense / budget management ("Ausgaben", cashbook)
- **What:** record expenses (cost, date, category, trip), per-trip budget and a "warning range"
  threshold, totals, remaining budget, per-category breakdown, CSV export.
- **How:** `AusgabeService` scopes every read and write to the authenticated user
  (`findByUserAndDateBetween`, `findByUserAndTrip`, `findByIdAndUser`) and rejects an expense on a
  trip the user is not a member of. Budget and warning range are stored **per member per trip** on
  the `user_trips` join row (`budget`, `warningRange` columns), so each participant has their own
  budget for a shared trip. CSV export is served with `produces = "text/csv"`.
- **Files:** `ausgabe/AusgabeController.java`, `AusgabeService.java`, `AusgabeRequestDTO`,
  `AusgabeResponseDTO`, `KategorieSummeDTO`, `entities/Ausgabe.java`, `entities/Categories.java`;
  `frontend/src/app/components/cashbook/`, `components/spending-overview/`,
  `components/trip-spending-overview/`, `src/app/services/expense.service.ts`,
  `src/app/models/expense.models.ts`.

### 4.10 Friends & friend requests
- **What:** search users, send/accept/decline friend requests, remove friends.
- **How:** `FriendsService`. Friendships are stored **symmetrically** in the `user_friends`
  many-to-many join table (both directions inserted). Notable rule: if B already has a pending request
  to A and A now sends one to B, the existing request is **auto-accepted** rather than creating a
  duplicate. Removing a friend also deletes the `ACCEPTED` `FriendRequest` rows in both directions, so
  a fresh request can be sent later. User search matches username, first name **or** surname,
  case-insensitively, excluding the searcher. Every state change fans out to two channels: a STOMP
  push and a best-effort email.
- **Files:** `friends/FriendsController.java`, `FriendsService.java`, `FriendRequestStatus.java`,
  `FriendRequestNotificationService.java`, `entities/FriendRequest.java`;
  `frontend/src/app/components/friends/`, `src/app/services/friends.service.ts`.

### 4.11 Trip & event invitations
- **What:** invite friends to a trip or to a single event; accept/decline; list incoming invites;
  remove a participant.
- **How:** `TripInviteService` enforces: cannot invite yourself, invitee must be a **friend**, inviter
  must be an **accepted** member of the trip; a `DECLINED` invite can be re-sent by resetting the
  existing row (the composite `(user_id, trip_id)` key forbids a second insert). Event invites
  (`EventService.inviteFriendToEvent`) require the inviter to be a participant and reject duplicates.
  `UserEvent` carries `status` (PENDING/ACCEPTED/DECLINED) *and* `creator` (who sent the invite).
  Both accept/decline paths push a STOMP message and email the inviter with the answer.
- **Files:** `tripManagement/controllers/TripInviteController.java`, `services/TripInviteService.java`,
  `services/TripInviteNotificationService.java`, `eventHandling/EventService.java` (invite methods),
  `EventInviteNotificationService.java`, `entities/UserTrip.java`, `entities/UserEvent.java`;
  `frontend/src/app/components/invite-dialog/`.

### 4.12 Privacy controls
- **What:** per-friend switches controlling whether that friend may see the owner's **events** and/or
  **profile**.
- **How:** `PrivacyPermission` is a row keyed by `(owner_id, friend_id)` (unique constraint) with two
  booleans, **both defaulting to false** — i.e. being friends alone grants nothing. `PrivacyService`
  returns 403 if the requester is not a friend, 403 if no permission row exists at all, and 403 if the
  specific flag is false.
- **Files:** `privacy/PrivacyController.java`, `PrivacyService.java`, `PrivacyPermissionDTO`,
  `SharedEventDTO`, `SharedUserProfileDTO`, `entities/PrivacyPermission.java`;
  `frontend/src/app/components/privacy-settings/`, `src/app/services/privacy.service.ts`.

### 4.13 AI event recommendations
- **What:** type a location, get 1–5 suggested public events in the next 7 days.
- **How:** `GeminiEventSearchService` posts a single `generateContent` request to the Gemini REST API
  with `responseMimeType=application/json`, `temperature=0.3`, `maxOutputTokens=4096`, and a prompt
  fixing the exact JSON shape. The reply is defensively unwrapped
  (`candidates[0].content.parts[*].text`), the JSON array is sliced out between the first `[` and last
  `]` (with a ```` ``` ```` code-fence fallback), and parsed with `ALLOW_UNQUOTED_CONTROL_CHARS`
  enabled to tolerate stray newlines inside the model's strings.
- **Important documented limitation (in the class Javadoc):** the `google_search` grounding tool was
  **removed** because it has zero free quota on non-billing Google Cloud projects, making every search
  fail with HTTP 429. Without grounding, Gemini answers from training knowledge, so results are
  best-effort suggestions, **not confirmed live listings** — the prompt itself instructs the model to
  say so.
- **Files:** `eventRecommendation/controllers/EventRecommendationController.java`,
  `services/GeminiEventSearchService.java`, `dto/EventRecommendationDTO.java`, `dto/EventSearchRequest.java`;
  `frontend/src/app/EventRecommendation/pages/event-search/`.

### 4.14 iCalendar export
- **What:** download all events and trips as a `.ics` file (`reisen.ics`).
- **How:** `DataService` builds VCALENDAR/VEVENT text by hand (no iCal library): timed VEVENTs for
  events, all-day VEVENTs for trips (with `DTEND` correctly made **exclusive** by adding one day),
  ICS special-character escaping, and CRLF line endings.
- **Files:** `FileManagement/DataController.java`, `DataService.java`, `IcalRecord.java`.
- **Gap:** this endpoint calls `eventRepository.findAll()` / `tripRepository.findAll()` — it exports
  **every user's** events and trips, not just the caller's. See §11.

### 4.15 Real-time + email notifications
- **What:** a notification bell with unread badge; live updates for friend requests, trip invites and
  event invites; the same events mirrored to the user's inbox.
- **How:** three `@Service` publishers (`FriendRequestNotificationService`,
  `TripInviteNotificationService`, `EventInviteNotificationService`) use `SimpMessagingTemplate
  .convertAndSendToUser(username, "/queue/...", dto)`. The frontend subscribes to seven user queues:
  `/user/queue/friend-requests`, `.../friend-requests/status`, `.../friend-removed`,
  `/user/queue/trip-invites`, `.../trip-invites/status`, `/user/queue/event-invites`,
  `.../event-invites/status`. `EmailNotificationService` provides `trySend` (returns whether delivery
  succeeded — used when the reply depends on it, e.g. "code sent" vs "here is your code") and
  `sendBestEffort` (fire-and-forget — a failed SMTP server must never break an invite).
  The in-app notification list itself is client-side state persisted to `localStorage` (capped at 50).
- **Files:** `notification/EmailNotificationService.java`, the three `*NotificationService` classes,
  `websocket/WebSocketConfig.java`, `JwtHandshakeInterceptor.java`, `UserHandshakeHandler.java`;
  `frontend/src/app/services/websocket.service.ts`,
  `frontend/src/app/components/notification-menu/` (component + `notification.service.ts`).

### 4.16 Test-user seeding
- **What:** a `test-users` Spring profile that seeds three known accounts (`testuser`, `frienduser`,
  `strangeruser`, all password `Test1234!`) with friendships, trips, events and pending invites, so
  Playwright E2E tests and manual demos have deterministic data.
- **How:** `@Configuration @Profile("test-users")` exposing a `CommandLineRunner` that runs inside a
  `TransactionTemplate`; every `create*IfMissing` helper is idempotent, so restarting does not
  duplicate data.
- **Files:** `TestConfig/TestUserSeeder.java`, `TEST_USERS.md`, root `package.json`
  (`npm run docker:test-users`).

---

## 5. Data model

Schema is generated by Hibernate from the entities (`spring.jpa.hibernate.ddl-auto=update`).
There are no migration files. `data.sql` seeds five categories.

**User** (`users`)
- `id` (AUTO), `name` (unique, not null — the login username), `firstName`, `surname`,
  `email` (unique, not null), `passwordHash` (not null, BCrypt), `provider` (`"LOCAL"` | `"GOOGLE"`,
  default `LOCAL`), `dateOfBirth`, `securityQuestion`, `securityAnswerHash` (BCrypt),
  `emailVerified` (not null, default false), `verificationCode`, `verificationCodeExpiresAt`.
- Relations: `@OneToMany` → `UserTrip`; `@ManyToMany` → `Event` via join table `user_events`;
  `@ManyToMany` self-referencing → `friends` via join table `user_friends`; `@OneToMany` → `Ausgabe`.

**Trip**
- `id`, `name` (not null), `startDate` (LocalDate), `endDate` (LocalDate).
- Relations: `@OneToMany` → `Event`, `@OneToMany` → `Ausgabe`, `@OneToMany` → `UserTrip`.

**Event**
- `id`, `name` (not null), `startTime` (LocalDateTime), `endTime` (LocalDateTime), `kosten` (double).
- Relations: `@ManyToOne` → `Trip` (nullable — a "personal" event has no trip);
  `@ManyToOne` → `Categories`.

**UserTrip** (`user_trips`) — join entity with payload
- `@EmbeddedId UserTripId {userId, tripId}`; `@ManyToOne user`, `@ManyToOne trip`,
  `@ManyToOne invitedBy` (nullable), `status` enum `PENDING|ACCEPTED|DECLINED` (not null),
  `budget` (double, default 0), `warningRange` (double, default 0).
- Carries both trip membership/invitation state **and** the per-member budget.

**UserEvent** (`user_events`) — join entity with payload
- `id`, `@ManyToOne event` (not null), `@ManyToOne user` (not null — the participant/invitee),
  `@ManyToOne creator` (not null — who sent the invite), `status` enum `PENDING|ACCEPTED|DECLINED`.
- Note: `User.events` maps a plain `@ManyToMany` onto the **same** `user_events` table, so two
  mappings share one table (see §11).

**Ausgabe** (expense)
- `id`, `kosten` (double), `date` (LocalDate).
- Relations: `@ManyToOne` → `Trip`, `@ManyToOne` → `Categories`, `@ManyToOne(optional=false)` → `User`.

**Categories**
- `id`, `name`. `@OneToMany` → `Event`, `@OneToMany` → `Ausgabe`. Seeded with FOOD, TRANSPORT, HOTEL,
  LEISURE, OTHER; new ones are created on demand from free-text input.

**FriendRequest** (`friend_requests`)
- `id` (IDENTITY), `@ManyToOne sender` (not null), `@ManyToOne receiver` (not null),
  `status` enum `PENDING|ACCEPTED|DECLINED` (default PENDING), `createdAt` (Instant),
  `respondedAt` (Instant, set by `setStatus`).
- The *confirmed* friendship lives separately in `user_friends`; this table records the request lifecycle.

**PrivacyPermission** (`privacy_permissions`)
- `id`, `@ManyToOne owner` (not null), `@ManyToOne friend` (not null),
  `canSeeEvents` (boolean, not null, default false), `canSeeProfile` (boolean, not null, default false).
- Unique constraint on `(owner_id, friend_id)`. Directional: Mike→Louie is a different row from Louie→Mike.

**GmailAccount** (`gmail_accounts`)
- `id`, `userId` (not null, **unique** — one Gmail account per user), `gmailEmail` (not null),
  `encryptedRefreshToken` (not null, length 2048, AES-256-GCM ciphertext), `scope`,
  `connectedAt`, `updatedAt`.
- Note: `userId` is a plain `long` column, not a JPA relation to `User`.

---

## 6. API

Base path `/api`. **Auth = "No"** only for `/api/account/**` (permitted in `SecurityConfig`);
every other endpoint requires a valid `Authorization: Bearer <jwt>` header
(`anyRequest().authenticated()`). `OPTIONS /**` and the `/ws/**` handshake are also permitted.

### Account — `AccountManagementController` (public)
| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/api/account/register` | Create account, send verification code | No |
| POST | `/api/account/login` | Username-or-email + password → JWT | No |
| POST | `/api/account/oauth/google` | Verify Google ID token → JWT (auto-provisions user) | No |
| POST | `/api/account/verify-email` | Confirm the emailed code | No |
| POST | `/api/account/forgot-password` | Check the security answer | No |
| POST | `/api/account/send-reset-code` | Email a password-reset code | No |
| POST | `/api/account/reset-password-with-code` | Reset password using the code | No |
| POST | `/api/account/reset-password` | Reset password using the security answer | No |

### Profile — `ProfileManagementController`
| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/api/profile` | Own profile details (incl. provider, verified flag) | Yes |
| POST | `/api/profile/send-change-code` | Email a one-time password-change code | Yes |
| POST | `/api/profile/change-password` | Change password (code or Google proof) | Yes |
| DELETE | `/api/profile` | Delete account and all owned data | Yes |

### Events — `EventController`
| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/api/event/create/manual` | Create an event from form input | Yes |
| GET | `/api/event/pull/all_events` | All events the user takes part in (calendar feed) | Yes |
| POST | `/api/event/import/parse` | Parse an uploaded `.eml` (multipart) into an unsaved event | Yes |
| POST | `/api/event/import/imap/messages` | List recent messages from an IMAP mailbox | Yes |
| POST | `/api/event/import/imap/parse` | Parse one IMAP message (by UID) into an unsaved event | Yes |
| POST | `/api/event/create/import` | Persist a previously parsed/imported event | Yes |
| POST | `/api/event/delete` | Delete an event (id in body) | Yes |
| POST | `/api/event/edit/{id}` | Update an event | Yes |
| GET | `/api/event/conflict` | Events overlapping a given interval | Yes |
| POST | `/api/event/{eventId}/invite/{friendUserId}` | Invite a friend to an event | Yes |
| DELETE | `/api/event/{eventId}/participants/{friendUserId}` | Remove a participant | Yes |
| PUT | `/api/event/{eventId}/respond` | Accept/decline an event invite | Yes |
| GET | `/api/event/{eventId}/invites` | Invites the caller sent for this event | Yes |
| GET | `/api/event/{eventId}/participants` | All participants of an event | Yes |
| GET | `/api/event/invites/incoming` | Caller's pending event invites | Yes |

### Gmail import — `GmailImportController`
| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/api/gmail/connect` | Exchange OAuth code, store encrypted refresh token | Yes |
| GET | `/api/gmail/status` | Whether Gmail is connected, and which address | Yes |
| GET | `/api/gmail/messages` | Recent message previews for the picker | Yes |
| POST | `/api/gmail/import/parse` | Parse one Gmail message into an unsaved event | Yes |
| POST | `/api/gmail/import/auto` | Parse many; keep only real events; report failures | Yes |
| DELETE | `/api/gmail/disconnect` | Delete the stored Gmail connection | Yes |

### Trips — `TripController`
| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/api/trips` | Trips the caller is a member of | Yes |
| GET | `/api/trips/{id}` | One trip (403 unless accepted member) | Yes |
| POST | `/api/trips` | Create a trip (caller becomes first member) | Yes |
| DELETE | `/api/trips/{id}` | Delete a trip + its events, expenses, memberships | Yes |
| DELETE | `/api/trips/{tripId}/friends/{friendUserId}` | Remove a member from a trip and its events | Yes |

### Trip events — `TripEventController`
| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/api/trips/{tripId}/events` | Events of a trip | Yes |
| POST | `/api/trips/{tripId}/events` | Add an event to a trip (resolves/creates category) | Yes |
| DELETE | `/api/trips/{tripId}/events/{eventId}` | Delete an event of a trip | Yes |
| PUT | `/api/trips/{tripId}/events/{eventId}/move/{newTripId}` | Move an event to another trip | Yes |

### Trip invites — `TripInviteController`
| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/api/TripInvites/{tripId}/{friendId}` | Invite a friend to a trip | Yes |
| POST | `/api/TripInvites/{tripId}/accept` | Accept a trip invite | Yes |
| POST | `/api/TripInvites/{tripId}/decline` | Decline a trip invite | Yes |
| GET | `/api/TripInvites/incoming` | Caller's pending trip invites | Yes |

### Expenses — `AusgabeController`
| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/api/ausgabe` | Add an expense | Yes |
| DELETE | `/api/ausgabe/{id}` | Delete one of the caller's expenses | Yes |
| GET | `/api/ausgabenliste` | Caller's expenses in a date range | Yes |
| GET | `/api/proKategorie` | Caller's expense sums per category | Yes |
| GET | `/api/ausgabenlisteTrip` | Caller's expenses for one trip | Yes |
| GET | `/api/proKategorieTrip` | Per-category sums for one trip | Yes |
| GET | `/api/budget` | Caller's budget for a trip | Yes |
| PUT | `/api/budget` | Update the caller's budget for a trip | Yes |
| GET | `/api/warningRange` | Caller's warning threshold for a trip | Yes |
| PUT | `/api/warningRange` | Update the warning threshold | Yes |
| GET | `/api/gesamtausgabenTrip` | Total spent on a trip | Yes |
| GET | `/api/verfuegbar` | Remaining budget for a trip | Yes |
| GET | `/api/ausgabenliste/export` | CSV export of expenses (`text/csv`) | Yes |
| GET | `/api/ausgabenlisteTrip/export` | CSV export of one trip's expenses | Yes |

### Friends — `FriendsController`
| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/api/friends` | Caller's friends | Yes |
| GET | `/api/friends/search?name=` | Search users by username/first name/surname | Yes |
| DELETE | `/api/friends/{userId}` | Remove a friend (both directions) | Yes |
| POST | `/api/friends/requests/{userId}` | Send a friend request | Yes |
| POST | `/api/friends/requests/{requestId}/accept` | Accept a request | Yes |
| POST | `/api/friends/requests/{requestId}/decline` | Decline a request | Yes |
| GET | `/api/friends/requests/incoming` | Pending incoming requests | Yes |
| GET | `/api/friends/requests/outgoing` | Pending outgoing requests | Yes |

### Privacy — `PrivacyController`
| Method | Path | Purpose | Auth |
|---|---|---|---|
| GET | `/api/privacy/permissions` | Caller's per-friend privacy settings | Yes |
| PUT | `/api/privacy/permissions/{friendId}` | Update what one friend may see | Yes |
| GET | `/api/privacy/users/{userId}/profile` | Another user's profile, if permitted | Yes |
| GET | `/api/privacy/users/{userId}/events` | Another user's events, if permitted | Yes |

### Misc
| Method | Path | Purpose | Auth |
|---|---|---|---|
| POST | `/api/trips/event-search` | AI event recommendations for a location | Yes |
| GET | `/api/categories/recent` | 10 most recently created categories | Yes |
| GET | `/api/data/ical` | iCalendar (`.ics`) export — **currently exports all users' data** | Yes |

**Total: 75 endpoints across 13 controllers — 8 public, 67 authenticated.**

---

## 7. Authentication & security, step by step

**Registration → login (local account)**
1. `POST /api/account/register`. Server validates username/email/password shape, rejects duplicate
   username or email, BCrypt-hashes the password and the favorite-colour security answer, generates a
   6-digit `SecureRandom` code with a 15-minute expiry, saves the user with `emailVerified=false`.
2. The code is emailed. If SMTP throws, the code is returned in the response message instead.
3. `POST /api/account/verify-email` with email+code → checks expiry, checks equality, sets
   `emailVerified=true`, clears the code.
4. `POST /api/account/login` with username **or** email + password. BCrypt `matches`; login is refused
   if `emailVerified` is false. On success `JwtService.createToken(user)` is called.

**Token issuance and validation**
5. `JwtService` builds the JWT **by hand**: `{"alg":"HS256","typ":"JWT"}` header, a payload with
   `sub`, `username`, `email`, `iat`, `exp` (assembled by string concatenation with a minimal `escape`
   helper), base64url-encoded, signed with `HmacSHA256` over `header.payload` using `jwt.secret`.
   Lifetime is **24 hours** (`TOKEN_SECONDS = 24*60*60`).
6. The frontend stores the token in `localStorage['sepToken']` (and the username in `sepUser`).
   `authGuard` simply checks that the key exists; `authInterceptor` attaches the bearer header.
7. On each request, `JwtAuthFilter` reads `Authorization: Bearer ...`, recomputes the HMAC and compares
   it to the third segment, decodes the payload, checks `exp` against now, and extracts the `username`
   claim with a regex. On success it sets an authenticated `UsernamePasswordAuthenticationToken`
   (with an **empty authority list** — the app has no roles). On any `IllegalArgumentException` it
   silently leaves the context unauthenticated, and the filter chain then returns 401.
8. `SecurityConfig`: CSRF disabled, `SessionCreationPolicy.STATELESS`, CORS with
   `allowedOriginPatterns("*")` and all headers/methods, `permitAll` on `/api/account/**`, `/ws/**`
   and `OPTIONS /**`, `authenticated()` on everything else.
9. Controllers/services convert the username back into a `User` row
   (`CurrentUserService.requireUser` → 401 if the account no longer exists) and then apply their own
   domain authorization (trip membership, friendship, invite ownership, privacy flags).

**Google login (ID-token flow)**
10. The browser gets an ID token from Google Identity Services using the public client id (served at
    runtime from `config.js`). `POST /api/account/oauth/google` verifies the token's signature and
    audience with `GoogleIdTokenVerifier`, requires `email_verified`, finds-or-creates the user, and
    issues the app's own JWT. **No client secret is involved.**

**Gmail OAuth (authorization-code flow — the only place a client secret is used)**
11. Frontend generates a random `state`, stores it in `sessionStorage`, and redirects to Google with
    `scope=gmail.readonly`, `access_type=offline`, `prompt=select_account consent`.
12. Google redirects to `/gmail-import?code=...&state=...`. The component **compares the returned
    `state` to the stored one** (CSRF defence) and clears it, then posts the code to the backend.
13. Backend exchanges the code for tokens, encrypts the refresh token (AES-256-GCM, random IV per
    message) and stores it. Access tokens are re-derived from the refresh token on every use and never
    persisted.

**WebSocket auth**
14. The STOMP handshake carries the JWT as a **query parameter** (`/ws?token=...`).
    `JwtHandshakeInterceptor` validates it and returns `false` (rejecting the handshake) if it is
    absent or invalid. The username becomes the STOMP `Principal`, which scopes `/user/queue/**`
    deliveries.

**Secret handling**
- `.env` at the repo root (git-ignored; `.env.example` is committed) supplies `SPRING_MAIL_*`,
  `GEMINI_API_KEY`, `GOOGLE_OAUTH_CLIENT_ID`, and (per `compose.yaml`) `GOOGLE_OAUTH_CLIENT_SECRET`
  and `GMAIL_TOKEN_ENCRYPTION_SECRET`. `spring-dotenv` loads it into Spring properties.
- The frontend's Google **client id** (public by design) is injected at container start by
  `docker/40-app-config.sh`, which rewrites `config.js` from `GOOGLE_OAUTH_CLIENT_ID` — so the same
  image works in any environment without a rebuild.
- `application.properties` ships dev defaults for `jwt.secret`, the Postgres password, the Google
  client id and `gmail.token.encryption-secret`. These are committed and **must** be overridden in
  production (`AGENTS.md` says so explicitly).

---

## 8. DevOps

**Docker (`compose.yaml`, three services)**
- `db` — `postgres:18`, DB/user `sep`, password `sepsep123`, volume `db_data`, host port **5433 → 5432**.
- `backend` — built from `backend/Dockerfile` (two stages: `gradle:jdk21` runs
  `./gradlew clean bootJar -x test`; runtime is `eclipse-temurin:21-jre` running the fat jar).
  Listens on 8080 in-container, published on host **8082**. Receives DB, SMTP, Gemini, Google OAuth
  and Gmail-token env vars from `.env`.
- `frontend` — built from `frontend/Dockerfile` (stage 1 `node:22-alpine` runs
  `npm ci && npm run build -- --configuration production`; stage 2 `nginx:alpine` serves
  `dist/sep/browser`). Published on host **4200 → 80**. `docker/40-app-config.sh` runs via nginx's
  `/docker-entrypoint.d/` to regenerate `config.js` from `GOOGLE_OAUTH_CLIENT_ID`.
- `nginx.conf` proxies `/api/` → `http://backend:8080/api/` with **300 s** connect/send/read timeouts
  (the default 60 s produced 504s on AI-backed imports), sets `Cache-Control: no-store` on
  `index.html` and `config.js`, and `immutable, max-age=31536000` on content-hashed `js/css/woff`.
- `SPRING_PROFILES_ACTIVE=test-users` activates the seeder (`npm run docker:test-users`).

**CI — GitLab (`.gitlab-ci.yml`), three stages, five jobs**
1. `lint`
   - `backend_lint` — `eclipse-temurin:21-jdk`, runs `./gradlew spotlessCheck`, caches `.gradle/`.
   - `frontend_lint` — `node:22-bookworm-slim`, `npm ci` + `npm run lint`, caches `frontend/.npm/`.
2. `build` (only on default branch, MR events, and tags)
   - `build_frontend` / `build_backend` — Docker-in-Docker (`docker:26` + `docker:26-dind`, BuildKit
     enabled), build the image, push to `$CI_REGISTRY_IMAGE/{frontend,backend}` tagged with the short
     SHA, the branch slug, `latest` on the default branch, and the git tag when present.
3. `test`
   - `test_backend` — `./gradlew test`, publishes JUnit XML as a GitLab test report plus the HTML
     report (1-week artifact).
   - `test_frontend_playwright` — `mcr.microsoft.com/playwright:v1.59.1-noble`,
     `npx playwright test --reporter=list`; Playwright's `webServer` config boots `npm start` itself.
     Report + traces kept for 1 week.
- Runners are tagged `dind` + `ude-sse` (the university's shared runners).
- **Note:** the `build` stage does not depend on `test` passing — images are built in parallel with
  tests, and `Dockerfile` explicitly skips tests (`-x test`).
- There is **no deploy stage**; the pipeline stops at pushing images to the registry.

---

## 9. Technical challenges solved, and trade-offs

Inferred from code comments and git history (commit subjects quoted).

1. **AI imports made every event land on "today".** A failed Gemini call (typically HTTP 429 quota)
   used to fall back to `LocalDateTime.now()`, so every imported mail became an event on the import
   date. Fixed by making a failure **throw** (`EventDetectionException`) instead of fabricating a date,
   and by skipping mails whose start date is blank/unparseable. Commits: `fix: import events on their
   real date instead of today`, `fix: make Gmail auto-import reliable and honest about failures`.
   The code comment is explicit: *"Ein fehlgeschlagener Gemini-Aufruf … darf NICHT als Event mit dem
   heutigen Datum 'erfunden' werden."*

2. **Relative dates in mails.** "morgen"/"nächsten Montag" were being resolved against *today* rather
   than the mail's send date. Solved by injecting the message's `sentDate` (plus weekday name) into the
   prompt as an explicit reference date (`resolveSentDate`, `formatReferenceDate`).

3. **Gemini rate limits vs. request timeouts — a tuning loop visible in the history.** Auto-import
   parallelism went `1 → 6 → 10`, a "batch everything into a single Gemini call" attempt was tried and
   **reverted**, and a general "speed up" commit was **reverted** too (`Revert "perf: batch Gmail
   auto-import into a single Gemini call"`, `Revert "perf: speed up Gmail auto-import"`). The code
   settled on `AUTO_IMPORT_PARALLELISM = 3` with a documented rationale: low enough not to trip the
   per-minute quota (429), with 2 retries and linear backoff spacing out the rest. Trade-off: slower
   bulk imports in exchange for reliability.

4. **504 on auto-import.** Long AI-backed requests were being cut off by Nginx's default 60 s proxy
   timeout; raised to 300 s (`fix: prevent 504 on Gmail auto-import`).

5. **Grounded web search was abandoned.** Event recommendations originally used Gemini's
   `google_search` tool, which has **zero free quota on non-billing GCP projects** → every search 429'd.
   Trade-off taken: drop grounding, accept that recommendations come from training knowledge, and make
   the prompt tell the model to label them as suggestions rather than confirmed listings.

6. **Non-event mails polluting the calendar.** Bulk import initially created an event from every
   selected mail. A Gemini **classification** step was added (`hasEvent`), plus a rule that events
   without a real name (`"Unbekanntes Event"`) are dropped. Commits: `feat: auto-import only Gmail
   messages that contain an event`, `feat: drop Gmail import mode; skip eventless/unnamed mails`.
   The distinction between "genuinely no event" (skip quietly) and "could not check" (count as
   `failed` and report) is deliberate and documented.

7. **Account deletion kept failing on foreign keys.** With `ddl-auto=update` and no cascades, deleting
   a user broke on `user_friends`, `friend_requests`, `user_events` (including rows where the user was
   the *creator* of someone else's invite), `user_trips.invited_by_id`, and `ausgabe`. Solved with a
   long, explicitly-ordered manual cascade in `ProfileManagementService.deleteAccount`, including
   re-anchoring sent invites onto the invitee and garbage-collecting orphaned trips/events.
   Commits: `Fix account deletion failing when user has trips, events, or expenses`,
   `Implementierung Kaskadierendes Löschen`, `Extend notifications to friends/event invites and fix
   account deletion`.

8. **Trip endpoints were guessable.** `Secure trip endpoints and return readable error messages`
   introduced `TripService.requireMember`, funnelling every trip-scoped read/write through one
   membership check, and `GlobalExceptionHandler` so `ResponseStatusException` reasons reach the
   frontend instead of being blanked by Spring.

9. **Stale frontend after a rebuild.** `index.html` and `config.js` carry no content hash, so browsers
   kept serving the previous build's shell. Fixed with per-location `Cache-Control` rules in
   `nginx.conf` (`fix: stop the browser cache from surviving a frontend rebuild`).

10. **Gmail account could not be switched.** Reconnecting silently reused the currently signed-in
    Google account. Fixed by adding `select_account` to the consent `prompt` and exposing a
    `disconnect` endpoint (`fix: allow switching the connected Gmail account in the event import`,
    the current HEAD commit).

11. **A long-lived branch merge deleted a shipped feature.** `fix: restore Gmail import lost in Dali
    merge` — the Gmail import was wiped by the `Dali` branch merge and had to be restored, followed by
    a cluster of reconciliation commits (`fix: reconcile EventService after merge`, `fix: add missing
    parseMailFileIfEvent method`, `fix: import event DTOs from new dto subpackage`).

12. **Deliberate email/notification trade-off.** `EmailNotificationService` exposes both `trySend`
    (caller needs to know whether it worked) and `sendBestEffort` (mail failure must never fail the
    business action, e.g. a trip invite still succeeds with SMTP down). Documented in the class Javadoc.

---

## 10. Interesting implementation details

- **The JWT is hand-rolled.** No `jjwt`, no `nimbus`. `JwtService` concatenates the JSON payload as a
  string, base64url-encodes it, and signs with `javax.crypto.Mac`. Claims are read back out with
  regular expressions (`Pattern.compile("\"" + key + "\":\"([^\"]+)\"")`). It does verify the HMAC and
  the `exp` claim (see §11 for the caveats).
- **AES-256-GCM refresh-token encryption with a documented salt decision.** `TokenCipher` uses Spring
  Security's `Encryptors.delux` with a *constant* key-derivation salt, and the comment explains why
  that is acceptable: ciphertext uniqueness comes from the per-message random GCM IV, not the salt.
- **Two-stage mail parsing, structured-data-first.** JSON-LD (`application/ld+json`) is preferred over
  the LLM because it is exact. The schema.org detection is unusually thorough: it handles `@context` as
  a string, an array, or an object; `@type` as a string, an array, or nested inside `@graph`; and the
  four common spellings of the Event type (`Event`, `schema:Event`, `http(s)://schema.org/Event`).
- **The "honest failure" pattern in auto-import.** `ParseOutcome(Optional<EventResponseDTO> event,
  boolean failed)` distinguishes *"this mail has no event"* from *"we could not determine whether it
  has one"*. The UI can then say "3 imported, 2 could not be checked" instead of silently losing mail.
- **Defensive LLM-output parsing.** `GeminiEventSearchService.stripJsonFences` slices between the first
  `[` and last `]` (falling back to stripping ``` ```json ``` fences), and parses with
  `ALLOW_UNQUOTED_CONTROL_CHARS` because the model sometimes emits literal newlines inside JSON
  strings. `tryParseDatetime` accepts four different datetime shapes and never throws.
- **Budget lives on the join row.** `UserTrip` carries `budget` and `warningRange`, so each member of a
  shared trip has their own budget — a neat use of a join entity with payload.
- **Mutual friend requests auto-resolve.** If two users independently request each other, the second
  request accepts the first instead of creating a duplicate.
- **Re-inviting after a decline mutates the existing row**, because the composite `(user_id, trip_id)`
  primary key forbids a second insert — the comment says exactly this.
- **Runtime frontend config without a rebuild.** `config.js` is regenerated from an env var at nginx
  start, so one built image serves any environment's Google client id.
- **iCalendar is generated by hand**, and correctly makes all-day `DTEND` exclusive (`+1 day`) —
  a detail most hand-rolled ICS writers get wrong.
- **Idempotent test-data seeder** behind a Spring profile, with every helper written as
  `create…IfMissing`, so restarting the stack never duplicates data.
- **Naming is bilingual.** Domain terms are German (`Ausgabe` = expense, `kosten` = cost,
  `Kategorie`, "Unbekanntes Event"), APIs are German (`/api/ausgabenliste`, `/api/proKategorie`,
  `/api/verfuegbar`), while newer code, Javadoc and commit messages are in English. Several German
  code comments remain.

---

## 11. Limitations, known gaps, unfinished work

**Security**
- **`GET /api/data/ical` leaks every user's data.** `DataService.exportAllEventsAsIcal()` calls
  `eventRepository.findAll()` and `tripRepository.findAll()` with no user filter — any authenticated
  user downloads all events and trips in the database.
- **Several event endpoints have no ownership check.** `POST /api/event/delete`,
  `POST /api/event/edit/{id}`, and `GET /api/event/{eventId}/participants` take an event id and never
  verify the caller participates in it — any authenticated user can edit or delete any event by id.
  (Trip-scoped endpoints *are* guarded, via `TripService.requireMember`.)
- **JWT signature comparison is not constant-time** (`sign(...).equals(parts[2])`), and the payload is
  parsed with regexes rather than a JSON parser. The `alg` header is never checked against the
  algorithm actually used.
- **Committed dev secrets.** `application.properties` ships a real default Google client id, a default
  Postgres password (`sepsep123`), `jwt.secret=sep-development-secret-change-me`, and
  `gmail.token.encryption-secret=sep-development-gmail-token-secret-change-me`; `compose.yaml` hard-codes
  the DB password. `AGENTS.md` acknowledges this is local-dev-only, but nothing enforces an override.
- **CORS is wide open** (`allowedOriginPatterns("*")`, all methods, all headers) in the global
  `SecurityConfig`, in addition to a scattering of per-controller `@CrossOrigin` annotations with
  inconsistent values (`http://localhost:4200`, `"*"`, `{4200, 4201}`).
- **IMAP credentials are sent to the backend in a request body** (`ImapMailboxRequestDTO` carries host,
  port, username, password) and are used to open a live `imaps` connection per request. They are not
  stored, but they do transit the API. [VERIFY] whether the IMAP UI is still reachable — the Gmail flow
  appears to have superseded it, but the endpoints and DTOs remain.
- **No roles/authorities.** `JwtAuthFilter` grants `List.of()` authorities; there is no admin concept.
- **No refresh tokens / no logout server-side.** Tokens live 24 h and logout just clears `localStorage`;
  a leaked token stays valid until it expires.

**Data model / persistence**
- **`spring.jpa.hibernate.ddl-auto=update`** — the schema is inferred from entities at boot. There are
  no migrations, no down-migrations, and no way to reproduce a schema change deterministically.
  `update` also never drops or narrows columns.
- **Two mappings share the `user_events` table**: `User.events` (a plain `@ManyToMany` join table) and
  the `UserEvent` entity (with `status` and `creator`). This overlap is fragile and is what
  `PrivacyService.getSharedEvents` reads (`owner.getEvents()`), which means shared events are read
  through the `@ManyToMany` view and may not agree with the `UserEvent`-based calendar view. [VERIFY]
- **`GmailAccount.userId` is a raw `long`**, not a foreign-key relation — deleting a user does not
  delete their Gmail connection. `ProfileManagementService.deleteAccount` does **not** clean up
  `gmail_accounts`, so a deleted user's encrypted refresh token is left behind.
- **All deletion cascades are hand-written** in Java rather than declared on the mappings, so every new
  relation risks a new orphan.
- **`Event.kosten` is a `double`** (money in floating point), as is `Ausgabe.kosten`, `UserTrip.budget`
  and `warningRange`.
- **Package/directory mismatch:** `backend/src/main/java/com/sep/sep_backend/FileManagement/` contains
  files declaring `package com.sep.sep_backend.filemanagement;`. It compiles (and works on
  case-insensitive Windows), but the directory was never renamed to match the commit
  `Normalize file management package name`.

**Functionality**
- **AI recommendations are not real listings** — no web grounding (see §9.5); the prompt itself
  instructs Gemini to disclaim them.
- **Gmail import is capped at 10 recent messages** by default (`gmail.import.max-messages`), and
  auto-import processes only what the user selects from those.
- **One Gmail account per user** (`gmail_accounts.userId` is unique).
- **In-app notifications are client-side only** — the bell's history lives in `localStorage`, capped at
  50. Notifications pushed over STOMP while the user is offline are lost (email is the fallback).
- **The WebSocket URL is hard-coded** to `ws://localhost:8082/ws` in `websocket.service.ts` — unlike
  the REST calls, it is not relative, so real-time features will not work behind the Docker/nginx
  setup or any non-localhost deployment. **[VERIFY] this looks like a real bug, not a config choice.**
- **The security question is fixed** for everyone: "What is your favorite color?".
- **No deployment stage in CI** — images are built and pushed, nothing deploys them.
- Angular is configured with `provideZoneChangeDetection` (zone.js), not zoneless.

**Testing**
- 24 backend test classes and 13 frontend `.spec.ts` files against ~14.4k lines of production code —
  coverage is partial. There are no backend tests for `gmailimport/GmailOAuthService`, `GmailClient`,
  `TokenCipher`, `websocket/*`, `JwtService`, or `DataService`'s access scoping. [VERIFY: measured by
  which test files exist, not by a coverage tool.]

---

## 12. Numbers

| Metric | Value |
|---|---|
| Backend Java (main), lines | 8,922 across 116 files |
| Backend Java (test), lines | 5,772 across 24 test classes |
| Frontend TypeScript (non-spec), lines | 5,491 |
| Frontend unit tests (Karma/Jasmine), lines | 1,106 across 13 `.spec.ts` files |
| Frontend HTML templates, lines | 2,663 |
| Frontend CSS, lines | 2,826 |
| Playwright E2E tests, lines | 1,149 across 8 spec files + 1 helper |
| **Total production code (backend main + frontend ts/html/css)** | **~19,900 lines** |
| **Total including tests** | **~27,900 lines** |
| REST endpoints | **75** (8 public, 67 authenticated) |
| REST controllers | 13 (+1 `@RestControllerAdvice`) |
| Backend `@Service` classes | 21 |
| JPA entities | 10 (+1 `@Embeddable` composite id) |
| Spring Data repositories | 10 |
| DB migrations | 0 (schema via `ddl-auto=update`; `data.sql` seeds 5 categories) |
| Angular components | 23 |
| Angular root-provided services | 13 |
| Angular routes | 10 (9 guarded by `authGuard`, 1 public: `/account`) |
| STOMP user queues subscribed | 7 |
| Git commits (current branch / HEAD) | **460** (384 non-merge, 76 merges) |
| Git commits across all branches | 515 |
| Git author identities | 15 |
| Distinct contributors | **8** (six team members + 2 setup/staff authors) |
| Project timespan | 2026-04-01 → 2026-07-13 |

**Contributors by commit count** (`git shortlog -sn HEAD`, git identities collapsed to people; sums
to the 460 commits reachable from HEAD — several people committed under two or three names):

| Person | Commits |
|---|---|
| Noah Bucic (`Noah Bucic`, `Bucic, Noah`) | 114 |
| Florian Krüßmann (`Florian Kruessmann`, `Florian`, `Krüßmann, Florian`) | 114 |
| Samy Hashem (`samy`, `Hashem, Samy`) | 86 |
| Nikolay Bozhkov (`bozhk`, `Bozhkov, Nikolay`) | 62 |
| Mohamed Salama (`MhamedSalama123`, `Salama Mohamed Mohamed, Mohamed`) | 42 |
| Auguste Cedrick Dibengue (`Dibengue, Auguste Cedrick`) | 28 |
| Alexander Korn (`Alexander Korn`, `Korn, Alexander`) | 12 |
| Maximilian Fiegen (`Fiegen, Maximilian`) | 2 |

The six names in the README's team table are Bucic, Krüßmann, Bozhkov, Dibengue, Hashem and Salama.
Korn and Fiegen appear only in the initial-setup/CI commits — likely course staff or the project
template author. [VERIFY]
