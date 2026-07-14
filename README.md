# TEAM-LOVELACE-BABBAGE

Eine webbasierte Reise- und Kalenderverwaltungsplattform mit Angular und Spring Boot.

---

# Features

* Erstellung von Accounts
* Anmeldung per Google
* Kalenderverwaltung
* Tripverwaltung
* Eventverwaltung
* Import von Events aus E-Mails (.eml-Upload, IMAP oder verbundenes Gmail-Konto) mit KI-Erkennung (Gemini)
* Export von Events als iCal
* Finanzverwaltung für Kosten & Ausgaben
* Empfehlungen & Suche von Events
* Hinzufügen von Freunden mit Privatsphäre-Einstellungen
* Koordination von Events und Trips mit Freunden
* Echtzeit-Benachrichtigungen (WebSocket)

---

# Technologien

## Frontend

* Angular 21
* TypeScript
* TailwindCSS
* Angular Material
* FullCalendar

## Backend

* Spring Boot 3.5
* REST API + WebSocket (STOMP)
* Java 21
* Gradle
* PostgreSQL
* Google Gemini (E-Mail-Import & Event-Empfehlungen)

## Deployment

* Docker

---

# Konfiguration

Vor dem Start `.env.example` nach `.env` kopieren und die Werte eintragen
(u. a. `GEMINI_API_KEY`, `SPRING_MAIL_*`, `GOOGLE_OAUTH_CLIENT_ID`):

```bash
cp .env.example .env
```

---

# Projekt starten

## Frontend

```bash
cd frontend
npm install
npm start
```

Frontend läuft unter:

```txt
http://localhost:4200
```

---

## Backend

Benötigt eine laufende Postgres-Instanz (`docker compose up db`).

```bash
cd backend
./gradlew bootRun
```

Backend läuft unter:

```txt
http://localhost:8082
```

---

## Datenbank

läuft mit PostgreSQL über:

```txt
postgresql://localhost:5433/sep
```

---

# Docker

Gesamtes System (Frontend, Backend, Datenbank):

```bash
docker compose up --build
```

Mit vordefinierten Test-Accounts (siehe `TEST_USERS.md`):

```bash
npm run docker:test-users
```

---

# Tests & Linting

## Frontend

```bash
cd frontend
npm test          # Unit-Tests (Karma + Jasmine)
npm run test:pw   # E2E-Tests (Playwright)
npm run lint      # ESLint
```

## Backend

```bash
cd backend
./gradlew test
./gradlew spotlessCheck   # Format-Check (Fix: ./gradlew spotlessApply)
```

---

# Voraussetzungen

* Node.js
* npm
* Java 21
* Docker

---

# Dokumentation

* `PROJECT_INFO.md` — detaillierte technische Übersicht (Architektur, Endpunkte, Datenmodell)
* `TEST_USERS.md` — vordefinierte Test-Accounts
* Weitere technische Dokumentation befindet sich im GitLab Wiki.

---

# Team

| Name                      | Rolle     |
|---------------------------|-----------|
| Bucic, Noah               | Fullstack |
| Krüßmann, Florian         | Fullstack |
| Bozhkov, Nikolay          | Fullstack |
| Dibengue, Auguste Cedrick | Fullstack |
| Hashem, Samy              | Fullstack |
| Salama, Mohamed           | Fullstack |
