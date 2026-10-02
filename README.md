# Pharmacy Management System

A pharmacy operations system for medicines, shelf locations, batches, stock, suppliers, purchases, prescriptions, dispensing, sales, payments, customers, returns, alerts, reports, users, and activity history.

- Frontend: Angular 19, PrimeNG, Tailwind CSS
- Backend: Spring Boot 3.4, Java 21
- Database: PostgreSQL 16
- Authentication: JWT access tokens and rotating refresh tokens, with role permissions

## Run with Docker

Copy `.env.example` to `.env` and replace every secret. `JWT_SECRET` must be at least 32 bytes. `DB_PASSWORD` and `PHARMACY_ADMIN_PASSWORD` are required.

```bash
docker compose up --build
```

Open `http://localhost` (or the port in `WEB_PORT`). Sign in with `PHARMACY_ADMIN_USERNAME` and `PHARMACY_ADMIN_PASSWORD`. The first sign-in asks for a new password.

The web container serves the Angular application and proxies `/api/` to the backend. PostgreSQL data is stored in the `pharmacy-data` volume.

## Hot reload

Use this while changing the frontend. The database and API stay in Docker. Saving a file updates the open browser.

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build web
```

Open the same address as `WEB_PORT` (http://localhost:8088 for the local stack). To go back to the production build:

```bash
docker compose up -d --build web
```

`npm start` in `frontend` also serves http://localhost:4200 with hot reload, and proxies `/api` to http://localhost:8080.

## Run locally for development

Requirements: Java 21, Maven 3.9, Node.js 22, and PostgreSQL 16.

Create the database and user, then start the API:

```bash
# Windows PowerShell
$env:DB_PASSWORD = "pharmacy"
$env:JWT_SECRET = "local-dev-secret-must-be-32-bytes-min"
$env:PHARMACY_ADMIN_PASSWORD = "Admin12345"
mvn -f backend/pom.xml spring-boot:run
```

The API listens on port 8080. Default database URL is `jdbc:postgresql://localhost:5432/pharmacy` with username `pharmacy`.

Start the UI:

```bash
cd frontend
npm install
npx ng serve
```

Open `http://localhost:4200`. The dev server proxies `/api` to `http://localhost:8080`.

## Configuration

Sensitive values come from environment variables. Do not commit `.env`.

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | HMAC key, minimum 32 bytes |
| `JWT_ACCESS_MINUTES` | Access token lifetime, default 15 |
| `JWT_REFRESH_HOURS` | Refresh token lifetime, default 12 |
| `CORS_ORIGINS` | Allowed browser origins, comma-separated |
| `PHARMACY_ADMIN_USERNAME` | Bootstrap administrator, default `admin` |
| `PHARMACY_ADMIN_EMAIL` | Bootstrap email |
| `PHARMACY_ADMIN_PASSWORD` | Bootstrap password, required on first start |
| `PHARMACY_ADMIN_MUST_CHANGE_PASSWORD` | Force a password change, default `true` |
| `LOGIN_MAX_FAILURES`, `LOGIN_LOCK_MINUTES` | Sign-in lockout, default 5 failures and 15 minutes |

Business values such as pharmacy name, currency, timezone, expiry warning days, expired-stock authorization, and the maximum discount percent are stored in Settings after the system is running. The default currency is USD and the default report timezone is UTC. Change `pharmacy.timezone` to the pharmacy's IANA timezone, for example `Africa/Nairobi`.

## Tests

```bash
mvn -f backend/pom.xml test
```

Integration tests start PostgreSQL with Testcontainers and need a running Docker engine.

```bash
cd frontend
npx ng build --configuration production
```

## Documentation

- [User guide](docs/USER_GUIDE.md)
- [Backup and recovery](docs/BACKUP.md)
- [Design decisions](docs/DECISIONS.md)
