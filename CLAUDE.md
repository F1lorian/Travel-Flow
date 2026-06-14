# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

SEP is a travel-planning application. Users create **trips**, attach **events** to trips (manually or by importing email files), track **expenses** (Ausgaben), and get AI-powered event recommendations. The app requires email verification on registration.

**Stack:** Angular 20 (frontend) + Spring Boot 3.5 / Java 21 (backend) + PostgreSQL

## Commands

### Backend (run from `backend/`)

```bash
./gradlew bootRun          # start dev server on :8082
./gradlew test             # run all tests
./gradlew test --tests "com.sep.sep_backend.EventHandling.EventServiceTest"  # single test class
./gradlew spotlessApply    # auto-format Java (Google Java Format)
./gradlew check            # tests + spotless format check (run before committing)
```

### Frontend (run from `frontend/`)

```bash
npm start      # ng serve with proxy → http://localhost:4200
ng test        # Karma unit tests
ng build       # production build → dist/
```

### Docker (full stack, run from repo root)

```bash
cp .env.example .env   # fill in GEMINI_API_KEY and mail credentials
docker compose up      # starts db (:5433), backend (:8082), frontend (:4200)
```

## Environment Setup

Copy `.env.example` to `.env` and provide:
- `GEMINI_API_KEY` — required for AI event recommendations
- `SPRING_MAIL_*` — Gmail SMTP credentials for email verification

For local development without Docker, the backend defaults to `localhost:5433/sep` with user `sep` / password `sepsep123`. The dev JWT secret defaults to `sep-development-secret-change-me`.

## Architecture

### Backend modules (`backend/src/main/java/com/sep/sep_backend/`)

| Package | Responsibility |
|---|---|
| `accountmanagement/` | Registration, email verification, login, password reset. Hand-rolled JWT (HmacSHA256, 24h TTL). No Spring Security filter chain — JWT is only issued, not validated server-side on other endpoints yet. |
| `TripManagement/` | CRUD for trips, trip-scoped events, and categories. `controllers/`, `services/`, `dto/` sub-packages. |
| `EventHandling/` | General event CRUD + iCal email import (`EventController`, `EventService`). |
| `EventRecommendation/` | Calls Gemini API (`GeminiEventSearchService`) to suggest events for a location. |
| `ausgabe/` | Expense tracking per trip (German "Ausgaben"). Budget and warning-range management. |
| `FileManagement/` | iCalendar export of all events (`GET /api/data/ical`). |
| `Entitys/` | JPA entities: `Trip`, `Event`, `Ausgabe`, `Categories`. Note: package name is misspelled `Enitys` — do not rename without updating all references. |
| `repos/` | Spring Data JPA repositories for `Ausgabe`, `Categories`, `Event`, `Trip`. |
| `exception/` | `GlobalExceptionHandler` + `EventOutsideTripException`. |

Schema is managed by `spring.jpa.hibernate.ddl-auto=update` — no migration files.

### Frontend modules (`frontend/src/app/`)

All components are **standalone** (no NgModule). The locale is set to `de-DE`.

| Path | Responsibility |
|---|---|
| `account-management/` | Login, register, email verify, forgot/reset password. Stores `sepToken` and `sepUser` in `localStorage`. |
| `TripManagement/` | Trip list page, trip detail page (events + AI recommendations), `TripService` (all `/api/trips` calls). |
| `components/calendar/` | Calendar view of all events. |
| `components/cashbook/` | Cashbook / expense entry form. |
| `components/spending-overview/` | Date-range expense summary with category breakdown. |
| `components/trip-spending-overview/` | Per-trip budget, spending, and warning range. |
| `EventRecommendation/` | Event search page (wraps Gemini recommendations). |
| `event-adding-manual/` | Models and service for the manual event-creation flow. |

**Routing** (`app.routes.ts`): All routes except `/account` are guarded by `authGuard`, which checks `localStorage.getItem('sepToken')`.

**API proxy**: `frontend/proxy.conf.json` forwards `/api/*` to `http://localhost:8082` during `ng serve`.

### Key cross-cutting patterns

- Backend controllers return DTOs, never raw JPA entities.
- `AccountManagementController` uses a local `@ExceptionHandler` for `IllegalArgumentException` → HTTP 400; other controllers use `GlobalExceptionHandler`.
- The Gemini integration requires a valid `GEMINI_API_KEY`; without it, event recommendations will fail silently or error.
- Java code must pass `spotlessCheck` (Google Java Format) before `./gradlew check` passes.
