# Gmail Import

Import events from Gmail emails (flight/hotel/restaurant confirmations, meeting invitations,
appointment confirmations, event tickets, ...) and turn them into calendar events after a
review-and-edit confirmation step.

The flow is: **Connect Gmail (OAuth 2.0) → Scan recent emails → Review & edit the extracted event
→ Create Event**. Nothing is written to the calendar until the user clicks *Create Event*.

---

## 1. Files added

### Backend (`backend/src/main/java/com/sep/sep_backend`)
- `gmailimport/GmailImportController.java` — thin REST API under `/api/gmail/**`.
- `gmailimport/GmailImportService.java` — orchestration/business logic (scan, de-dupe, create).
- `gmailimport/GmailOAuthService.java` — OAuth lifecycle: build consent URL, exchange code, store &
  refresh tokens.
- `gmailimport/GoogleOAuthClient.java` — HTTP client for Google's token endpoint.
- `gmailimport/GmailApiClient.java` — HTTP client for the Gmail REST API (list/read/profile).
- `gmailimport/EmailEventParser.java` — **reusable** parser: raw email → `ExtractedEventDTO`.
- `gmailimport/AiEventExtractor.java` — interface for AI extraction (keeps the parser testable).
- `gmailimport/GeminiEventExtractor.java` — Gemini-backed `AiEventExtractor`.
- `gmailimport/EventClassifier.java` — cheap offline check for "does this email hold an event?".
- `gmailimport/TokenCipher.java` — AES encryption of stored tokens.
- `gmailimport/GmailAuthException.java`, `GmailApiException.java`, `GmailRateLimitException.java` —
  typed errors mapped to HTTP 401 / 502 / 429.
- `gmailimport/dto/*` — `ExtractedEventDTO`, `GmailImportRequestDTO`, `GmailConnectionStatusDTO`,
  `GmailAuthUrlDTO`, `GmailCallbackRequestDTO`, `CreatedEventDTO`.
- `entities/GmailAccount.java` — per-user encrypted token storage.
- `repos/GmailAccountRepository.java` — repository for the above.

### Backend tests (`backend/src/test/java/com/sep/sep_backend/gmailimport`)
- `EmailEventParserTest.java`, `GmailOAuthServiceTest.java`, `GmailImportServiceTest.java`,
  `GmailImportControllerTest.java` (22 tests total).

### Frontend (`frontend/src/app/GmailImport`)
- `models/gmail-import.models.ts` — TypeScript interfaces mirroring the backend DTOs.
- `services/gmail-import.service.ts` — API client.
- `pages/gmail-import/gmail-import.ts` / `.html` / `.css` — the page: connect / scan / review / create.
- `pages/gmail-import/gmail-import.spec.ts`, `services/gmail-import.service.spec.ts` — 10 tests.

## 2. Files modified
- `backend/src/main/java/com/sep/sep_backend/entities/Event.java` — added optional
  `description`, `location`, `timeZone`, `organizer` columns so imported details are persisted.
- `backend/src/main/resources/application.properties` — Gmail OAuth / import / token-encryption config.
- `frontend/src/app/app.routes.ts` — added the guarded `/gmail-import` route.
- `frontend/src/app/app.html` — added a "Gmail Import" sidebar link.
- `.env.example`, `compose.yaml` — new environment variables.

## 3. How Gmail authentication works
This feature uses the **OAuth 2.0 authorization-code flow with offline access** (distinct from the
existing Google *sign-in*, which only verifies an ID token and needs no client secret):

1. Frontend calls `GET /api/gmail/auth-url`; the backend returns a Google consent URL requesting the
   read-only scope `https://www.googleapis.com/auth/gmail.readonly`, with `access_type=offline` and
   `prompt=consent` so Google returns a **refresh token**.
2. The browser is redirected to Google. After consent, Google redirects back to
   `GOOGLE_OAUTH_REDIRECT_URI` (the Angular `/gmail-import` route) with `?code=...`.
3. The page posts the code to `POST /api/gmail/connect`. `GmailOAuthService` exchanges it for an
   access token + refresh token via `GoogleOAuthClient`.
4. Both tokens are **encrypted** with `TokenCipher` (AES) and stored in the `gmail_accounts` table,
   one row per user.
5. On every Gmail call, `getValidAccessToken` checks expiry and **auto-refreshes** the access token
   with the stored refresh token when needed. Tokens are never returned to the frontend.

## 4. How event extraction works
`EmailEventParser` reads a raw RFC 822 message and extracts fields in order of reliability:

1. **schema.org JSON-LD** embedded in the email (used by many booking providers) — Event and
   Reservation types, including nested `reservationFor`, `location`, `organizer`, `address`.
2. **AI extraction** (`GeminiEventExtractor`) over the plain-text body when no structured data is
   present and a Gemini API key is configured. It returns strict JSON that is mapped to the fields.
3. **Heuristic fallback** — keep the subject as the title. Guarantees the feature works offline.

`EventClassifier` first decides whether an email looks like an event at all (structured data or
event keywords), so newsletters/marketing mail are ignored. Missing fields are left empty and the
parser **never throws**: an unreadable message is simply skipped. Extracted fields: title,
description, date, start time, end time, time zone, location, organizer. Duplicate emails are
de-duplicated by title + date + start time during a scan.

## 5. How to configure Gmail API credentials
1. In the [Google Cloud Console](https://console.cloud.google.com/): create/select a project and
   **enable the Gmail API** (APIs & Services → Library → Gmail API).
2. Configure the OAuth consent screen; while in *Testing*, add each tester's Gmail address.
3. Create an **OAuth client ID** of type **Web application**. Add an authorized redirect URI that
   exactly matches `GOOGLE_OAUTH_REDIRECT_URI`, e.g. `http://localhost:4200/gmail-import`.
4. Copy the client ID and client secret into your `.env` (see `.env.example`):
   ```
   GOOGLE_OAUTH_CLIENT_ID=...apps.googleusercontent.com
   GOOGLE_OAUTH_CLIENT_SECRET=...
   GOOGLE_OAUTH_REDIRECT_URI=http://localhost:4200/gmail-import
   GMAIL_TOKEN_ENCRYPTION_SECRET=<long random string>
   GEMINI_API_KEY=<optional, enables AI extraction>
   ```

## 6. How to run the feature locally
- **Everything via Docker:** `docker compose up --build`, then open `http://localhost:4200`, log in,
  and choose *Gmail Import* in the sidebar.
- **Manual:**
  - Backend: `cd backend && ./gradlew bootRun` (needs PostgreSQL; `docker compose up db` is enough).
  - Frontend: `cd frontend && npm install && npm start` (proxies `/api` to the backend).
- **Tests:** `cd backend && ./gradlew test` and `cd frontend && npx ng test --watch=false
  --browsers=ChromeHeadless`.

## 7. Assumptions made
- **Reused parsing pipeline, new richer parser.** The existing `.eml`-upload parser in
  `EventService` is covered by tests that pin its constructor, so instead of refactoring it, the
  Gmail feature introduces `EmailEventParser` as the reusable parser producing the richer field set.
- **Read-only Gmail scope** and a scan of the most recent inbox messages
  (`GMAIL_IMPORT_MAX_MESSAGES`, default 20). No mail is modified or sent.
- **Redirect URI points at the frontend** (`/gmail-import`); the Angular page forwards the code to
  the backend, which does the token exchange. The OAuth `state` is generated but not persisted; the
  callback is JWT-authenticated, which is the primary protection.
- **Event cost** is not part of email extraction; it defaults to `0` but can be entered by the user
  on the review form before creating the event.
- The `contextLoads` test is a no-op in this repo; new beans use standard constructor injection of
  existing bean types plus Spring Boot's autoconfigured `RestClient.Builder`.
- Five **pre-existing** frontend spec failures (`App should render title`, `Calendar`, `EventDialog`,
  `InviteDialog`, `Event`) are unrelated scaffold/Material-provider issues and were not introduced by
  this feature.
