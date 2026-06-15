and what abot# Repository Guidelines

## Project Structure & Module Organization
This repository contains a Spring Boot backend and an Angular frontend. Backend code lives in `backend/src/main/java/com/sep/sep_backend`, with JPA entities under `Enitys`, repositories under `repos`, and feature code such as `ausgabe` grouped by package. Backend configuration is in `backend/src/main/resources/application.properties`; tests are in `backend/src/test/java`.

Frontend code lives in `frontend/src/app`. Components are under `frontend/src/app/components`, shared models under `frontend/src/app/models`, and API-facing services under `frontend/src/app/services`. Static browser assets belong in `frontend/public`; global styles are in `frontend/src/styles.css`.

## Build, Test, and Development Commands
- `cd frontend && npm install`: install Angular dependencies.
- `cd frontend && npm start`: run the Angular dev server, usually on `http://localhost:4200`.
- `cd frontend && npm run build`: build the frontend into `frontend/dist`.
- `cd frontend && npm test`: run Karma/Jasmine frontend tests.
- `cd backend && .\gradlew bootRun`: run the Spring Boot API locally.
- `cd backend && .\gradlew test`: run JUnit backend tests.
- `cd backend && .\gradlew clean bootJar`: build the backend jar.
- `docker compose up --build`: build and run frontend, backend, and PostgreSQL together.

## Coding Style & Naming Conventions
Use the existing language conventions. Frontend files follow Angular naming such as `cashbook.component.ts`, `expense.service.ts`, and `expense.models.ts`. TypeScript uses two-space indentation and single quotes per `frontend/.editorconfig` and `package.json` Prettier settings. Java uses package-based organization; keep controllers, services, DTOs, entities, and repositories separated by responsibility. Use clear class names such as `AusgabeController`, `AusgabeRequestDTO`, and `TripRepository`.

## Testing Guidelines
Frontend tests use Jasmine/Karma and should sit next to the unit under test as `*.spec.ts`. Backend tests use JUnit Platform through Spring Boot; name tests `*Tests.java` and place them under the matching package in `backend/src/test/java`. Run both test suites before merge requests when changing shared behavior or API contracts.

## Commit & Pull Request Guidelines
Recent commits use short, descriptive subjects, often in German, for example `Verbindung zwischen Frontend und Backend` or `Uebersicht der Ausgaben`. Keep the first line concise and focused on the change.

Use the GitLab merge request template: include a short description, a bullet list of completed work, and a checklist. Link related issues when available. For frontend UI changes, include screenshots or a brief note about what was visually verified.

## Security & Configuration Tips
PostgreSQL defaults are currently committed for local development in `compose.yaml` and `application.properties`. Do not introduce production secrets into the repository; use environment-specific configuration for deployed systems.
