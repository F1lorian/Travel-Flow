# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## Overview

**SEP** — a German-language travel and calendar management app built by a university team.
Users keep a personal calendar of events, group events into **trips**, track **expenses/budget**
per trip, and coordinate with **friends** through invitations and per-friend privacy settings.
Events can be **imported from email** (uploaded `.eml`, IMAP, or a connected Gmail account),
parsed first from schema.org JSON-LD and, failing that, by asking **Gemini**.

Two independent apps plus Postgres, wired together by `compose.yaml`:

- `backend/` — Spring Boot 3.5.6 REST + WebSocket service (Java 21, Gradle)
- `frontend/` — Angular 21 SPA (npm, Tailwind v4, Angular Material, FullCalendar)

There is no root build orchestration; build and test each app from its own directory.

`PROJECT_INFO.md` in this directory is a detailed, code-verified briefing (architecture, all 75
endpoints, data model, known gaps). Read it when you need depth beyond this file.

## Commands

Backend (from `backend/`, `gradlew.bat` on PowerShell / `./gradlew` on Bash):

- Run locally: `./gradlew bootRun` (needs Postgres; `docker compose up db`)
- Tests: `./gradlew test` — single class: `./gradlew test --tests "com.sep.sep_backend.eventHandling.EventServiceTest"`
- **Format check: `./gradlew spotlessCheck`** — CI fails on this. Auto-fix with `./gradlew spotlessApply`.
- Jar: `./gradlew clean bootJar`

Frontend (from `frontend/`):

- Dev server: `npm start` (`ng serve` with `proxy.conf.json` → proxies `/api` to `localhost:8082`)
- Unit tests: `npm test` (Karma + Jasmine) · E2E: `npm run test:pw` (Playwright)
- Lint: `npm run lint` (ESLint + angular-eslint) — CI fails on this.

Full stack (from the repo root): `docker compose up --build` → frontend **4200**, backend **8082**,
Postgres **5433**. `npm run docker:test-users` additionally activates the `test-users` profile, which
seeds deterministic accounts (see `TEST_USERS.md`).

## Architecture

**Backend** — package root `com.sep.sep_backend`, organized by feature:
`accountmanagement`, `profilemanagement`, `eventHandling`, `eventRecommendation`, `gmailimport`,
`tripManagement`, `ausgabe` (expenses), `friends`, `privacy`, `notification`, `websocket`,
`FileManagement` (iCal export), plus shared `entities`, `repos`, `exception`, `SecurityConfig`.

Controller → Service → Spring Data repository. Controllers resolve the authenticated username into a
`User` (via `CurrentUserService.requireUser`); **services own the authorization rules** (trip
membership, friendship, invite ownership, privacy flags). Exceptions become `{"message": "..."}`
via `GlobalExceptionHandler`.

**Persistence** — PostgreSQL through Hibernate with **`spring.jpa.hibernate.ddl-auto=update`**: the
schema is derived from the JPA entities. There are **no migration files** — change an entity and the
column appears. Note `update` never drops or narrows columns, and **all deletion cascades are
hand-written in Java** (see `ProfileManagementService.deleteAccount`), so any new relation needs its
cleanup added by hand. `data.sql` seeds five expense categories.

**Auth** — stateless JWT. `JwtService` builds and verifies the token **by hand** (HMAC-SHA256 via
`javax.crypto.Mac`, claims read back with regexes) — there is no JWT library. `JwtAuthFilter` runs
before `UsernamePasswordAuthenticationFilter` and sets an authentication with **no authorities**
(the app has no roles). `SecurityConfig` permits `/api/account/**`, `/ws/**` and `OPTIONS`;
everything else requires a bearer token. Passwords are BCrypt.

Two distinct Google flows — do not conflate them:
- **Google login** uses an **ID token** verified with `GoogleIdTokenVerifier`. No client secret.
- **Gmail import** uses the **OAuth 2.0 authorization-code flow** and *does* need
  `GOOGLE_OAUTH_CLIENT_SECRET`. The refresh token is encrypted at rest (AES-256-GCM, `TokenCipher`);
  access tokens are never persisted.

**Realtime** — raw STOMP at `/ws`. The JWT travels as a **query parameter** (`/ws?token=...`),
validated by `JwtHandshakeInterceptor` during the handshake; `UserHandshakeHandler` binds the
username as the `Principal`, scoping `/user/queue/**`. In-memory broker; no persistence — a
notification pushed while the user is offline is lost (email is the fallback).

**Frontend** — Angular 21, standalone components, `src/app/`:
`components/` (most UI), `services/` (root-provided API services), `models/`, plus feature folders
`account-management/`, `profile-management/`, `TripManagement/`, `EventRecommendation/`.
`authInterceptor` attaches the JWT from `localStorage['sepToken']`; `authGuard` protects routes.
Note: the app uses `provideZoneChangeDetection` (zone.js), **not** zoneless.

## AI: Gemini, two separate integrations

Both read `gemini.api-key` / `gemini.model` (from `GEMINI_API_KEY` / `GEMINI_MODEL` in `.env`,
loaded by spring-dotenv).

1. **`EventService`** — uses the `google-genai` SDK (`Client.builder().apiKey(...)`) to *detect*
   whether a mail describes a real appointment and to *extract* `{name, kosten, startDate, endDate}`.
2. **`GeminiEventSearchService`** — calls the Gemini REST API directly with `RestClient`
   (`POST /v1beta/models/{model}:generateContent`) for event recommendations by location.

Two invariants worth preserving when touching this code:

- **A failed Gemini call must never fabricate a date.** It previously fell back to
  `LocalDateTime.now()`, which dumped every imported mail onto today. Failures now throw
  `EventDetectionException` and are reported as `failed`.
- **"No event" and "could not check" are different outcomes.** `ParseOutcome` keeps them apart so the
  UI can say "3 imported, 2 could not be checked" instead of silently losing mail.

Gmail auto-import runs at `AUTO_IMPORT_PARALLELISM = 3` with 2 retries and linear backoff —
deliberately tuned to stay under the free-tier per-minute quota. Raising it has been tried and
reverted; don't "optimize" it without a reason.

## Conventions

**Backend** — package-by-feature, each with its controller/service/DTOs and feature exceptions.
Constructor injection. REST base path `/api`. DTOs (records or POJOs) at the boundary.
**Run `spotlessApply` before committing** (google-java-format; `check` depends on `spotlessCheck`).

**Frontend** — standalone components, `@Injectable({providedIn:'root'})` services calling relative
`/api/**` paths, co-located `*.spec.ts`. Prettier: 100 cols, single quotes, Angular HTML parser.

**Naming is bilingual.** Domain terms and several endpoints are German (`Ausgabe` = expense,
`kosten` = cost, `/api/ausgabenliste`, `/api/proKategorie`, `/api/verfuegbar`), while newer code,
Javadoc and commit messages are English. Match the file you are editing rather than renaming things.

## Configuration & secrets

`.env` at the repo root (git-ignored; `.env.example` is committed) supplies `GEMINI_API_KEY`,
`SPRING_MAIL_*`, `GOOGLE_OAUTH_CLIENT_ID/SECRET/REDIRECT_URI` and `GMAIL_TOKEN_ENCRYPTION_SECRET`.
`application.properties` ships **dev-only defaults** for `jwt.secret`, the Postgres password and
`gmail.token.encryption-secret` — they are committed on purpose for local dev and must be overridden
in any real environment. Never add a real secret to a tracked file.

## Workflow

- `main` is protected on GitLab: **do not push to it.** Work on a branch and open a merge request.
- Before pushing: `./gradlew spotlessCheck test` (backend) and `npm run lint && npm test` (frontend) —
  CI runs exactly these and will fail the pipeline otherwise.
- Keep changes scoped to the feature you were asked about; ask before touching unrelated files.