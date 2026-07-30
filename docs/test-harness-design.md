# Aries Reporting test harness design

## Purpose

The harness validates the real HTTP journey across isolated data sources,
Flyway migrations, asynchronous export processing, authorization, and file
output. It never uses a developer database or real customer data.

## Architecture

```text
Testcontainers
  source PostgreSQL 16  ─┐
  reporting PostgreSQL 16├─> Spring Boot RANDOM_PORT ─> HttpClient + JWT
  Redis 7                ┤             │
  MailHog                ┘             └─> temporary export directory
```

`ReportingJourneyIT` starts every dependency, seeds users/accounts/transfers
into the source database, lets Flyway create the reporting schema, and removes
containers and temporary files after the test.

## Current journey harness

Actor matrix:

| Actor | Role | Purpose |
|---|---|---|
| admin@example.com | ADMIN | Run backfill |
| user@example.com | USER | Read statement, create and download export |
| other@example.com | USER | Verify job IDOR protection |

Journey:

1. Admin backfills transfers from the source; exactly one row is expected.
2. User reads the statement; `totalDebit=125.50`, count, and account scope are checked.
3. User creates an XLSX export; HTTP `202` and a job ID are expected.
4. The harness polls until `READY`, downloads the file, and checks content type,
   workbook structure, and account number.
5. A different user polls the job; HTTP `403` is expected.

## Test lanes

```powershell
# fast unit/context suite
powershell -NoProfile -ExecutionPolicy Bypass -File .codex\skills\aries-reporting-tests\scripts\run-tests.ps1 -Scope all

# focused security suite
powershell -NoProfile -ExecutionPolicy Bypass -File .codex\skills\aries-reporting-tests\scripts\run-tests.ps1 -Test "SecurityFilterChainTest,BearerTokenResolverTest,JwtAuthenticationProviderTest,JwtAuthFilterTest,JwtServiceTest,ReportControllerValidationTest,ExportControllerTest"

# PostgreSQL + Flyway enum integration
./mvnw.cmd -DskipITs=false -Dit.test=ReportingPostgresEnumIT verify

# HTTP journey harness
./mvnw.cmd -Dtest=AriesReportingProjectApplicationTests -DskipITs=false -Dit.test=ReportingJourneyIT verify
```

## Extension rules

- Each journey uses unique UUIDs and an isolated temporary export directory.
- Seed data stays minimal and deterministic; timestamps use UTC.
- Never log secrets, JWTs, passwords, exports, or `target/` output.
- New journeys must cover duplicate/retry, month-boundary, asynchronous failure,
  and ownership behavior when those contracts are in scope.
- Redis cache, MailHog inbox, PDF, restart/recovery, and performance are follow-up
  lanes; the current journey does not claim to cover them.

