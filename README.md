# TEAM-LOVELACE-BABBAGE

Eine webbasierte Reise- und Kalenderverwaltungsplattform mit Angular und Spring Boot.

---

# Features

* Erstellung von Accounts
* Anmeldung per Google
* Kalenderverwaltung
* Tripverwaltung
* Eventverwaltung
* Import & Export von Events
* Finanzverwaltung für Kosten & Ausgaben
* Empfehlungen & Suche von Events
* Hinzufügen von Freunden
* Koordination von Events und Trips mit Freunden


---

# Technologien

## Frontend

* Angular
* TypeScript
* TailwindCSS
* FullCalendar

## Backend

* Spring Boot
* Rest API
* Java 21
* Gradle

## Deployment

* Docker

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

```bash
docker compose up --build
```

---

# Tests

## Frontend (playwright)

```bash
npx playwright test
```

## Backend

```bash
./gradlew test
```

---
# Voraussetzungen

* Node.js
* npm
* Java 21
* Docker (optional)

---

# Dokumentation

Weitere technische Dokumentation befindet sich im GitLab Wiki.

---

# Team

| Name                      | Rolle     |
|---------------------------|-----------|
| Bucic, Noah               | Fullstack |
| Krüßmann, Krüßman         | Fullstack |
| Bozhkov, Nikolay          | Fullstack |
| Dibengue, Auguste Cedrick | Fullstack |
| Hashem, Samy              | Fullstack |
| Salama, Mohamed           | Fullstack |
