# Backup and recovery

This document is an operating procedure. The application does not run a backup job by itself, and a fresh install does not create backup files until someone follows these steps.

## What to back up

- The PostgreSQL database. It holds users, medicines, locations, batches, stock movements, purchases, prescriptions, dispensing, sales, payments, customers, returns, settings, and the activity history.
- The `.env` file, stored separately from the database backup, because it contains the database password, JWT secret, and bootstrap administrator password.
- This repository, or the built images, so the application version matches the database schema.

Uploaded files are not used. Stock and documents live in the database.

## Frequency

Take a full database backup at least once a day, after the pharmacy closes. Keep a copy from the last 7 days on the server and a copy off the server, for example on another disk or an encrypted store that is not the same machine. Take an extra backup before an upgrade.

## Create a backup

With Docker Compose running:

```bash
docker compose exec -T db pg_dump -U pharmacy -d pharmacy -Fc > pharmacy-backup.dump
```

On a local PostgreSQL install:

```bash
pg_dump -h localhost -U pharmacy -d pharmacy -Fc -f pharmacy-backup.dump
```

Copy `pharmacy-backup.dump` and a protected copy of `.env` to the off-server location. Confirm the dump file is not empty before deleting older copies.

## Restore

Stop the application so nobody writes during the restore. Restore into an empty database, or into a new database you have checked first.

```bash
docker compose stop backend web
docker compose exec -T db pg_restore -U pharmacy -d pharmacy --clean --if-exists < pharmacy-backup.dump
docker compose start backend web
```

For a local server, use `pg_restore -h localhost -U pharmacy -d pharmacy --clean --if-exists pharmacy-backup.dump`, then start the API again.

Sign in, open the dashboard, and confirm a recent sale or purchase is present before reopening the counter. If the restore is wrong, stop again and restore the previous dump. Do not continue selling on a database you have not checked.
