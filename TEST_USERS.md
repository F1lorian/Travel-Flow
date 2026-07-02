# Testnutzer starten

Normaler Start ohne Testnutzer:

```powershell
docker compose up --build
```

Start mit Testnutzer-Profil ohne neuen Build:

```powershell
docker compose build
npm run docker:test-users
```

Alternativ direkt mit Build und Testnutzer-Profil:

```powershell
$env:SPRING_PROFILES_ACTIVE='test-users'
docker compose up --build
```

Danach das Profil bei Bedarf wieder entfernen:

```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE
```

## Zugangsdaten

Alle Testnutzer verwenden dasselbe Passwort:

```text
Test1234!
```

| Nutzername | Passwort | Hinweis |
| --- | --- | --- |
| `testuser` | `Test1234!` | Ist mit `frienduser` befreundet. |
| `frienduser` | `Test1234!` | Ist mit `testuser` befreundet und hat Einladungen von ihm. |
| `strangeruser` | `Test1234!` | Ist nicht mit `testuser` befreundet. |

Die Testdaten bleiben im Docker-Datenbank-Volume gespeichert. Beim normalen Start wird der Seeder
nicht erneut ausgeführt, vorhandene Testdaten bleiben aber in der Datenbank.
