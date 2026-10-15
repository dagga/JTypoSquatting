# JTypoSquatting - Software Architecture

**Version:** 2.0
**See also:** [Technical architecture](TECHNICAL_ARCHITECTURE.md) (detailed, with UML diagrams) · [API](API.md) · [Deployment](DEPLOYMENT.md)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Runtime Modes](#2-runtime-modes)
3. [Components](#3-components)
4. [Scan Processing](#4-scan-processing)
5. [Domain Analysis Engine](#5-domain-analysis-engine)
6. [Data Architecture](#6-data-architecture)
7. [Security Architecture](#7-security-architecture)
8. [Concurrency and Limits](#8-concurrency-and-limits)
9. [Operations](#9-operations)
10. [Technology Stack](#10-technology-stack)
11. [Architecture Decisions](#11-architecture-decisions)
12. [Source Layout](#12-source-layout)

---

## 1. Overview

JTypoSquatting generates the typo variants of a domain name with 21 algorithms, checks each variant
(HTTP, TLS certificate, page content, screenshot) and streams the results to the user.

It is primarily a **public, multi-user web service**: a Next.js frontend and a Spring Boot backend, with
scans processed asynchronously by workers. The original **desktop application** (Swing) still works on
the same backend, in a dedicated local mode.

```
Browser ──► nginx ─┬─ /                       ─► Next.js (public pages + application)
                   └─ /api, /oauth2, /login,  ─► API instances (Spring Boot, profile "api")
                      /logout                       │
                                                    ├─► MySQL 8.4 (users, scans, results, cache, sessions)
                                                    ├─► S3-compatible storage (screenshots)
                                                    └─► OpenID Connect provider (Keycloak in development)

             Worker instances (Spring Boot + Chromium, profile "worker")
             ├─ claim queued scans in MySQL, check domains, store results and screenshots
             └─ isolated network: MySQL and S3 inside, Internet egress for the checks
```

---

## 2. Runtime Modes

The same backend artifact (`jtyposquatting-backend.jar`) runs in four configurations.

| Mode | How | HTTP | Scan worker | Security | Database |
|------|-----|------|-------------|----------|----------|
| Development (default) | `./gradlew :backend:bootRun` | API on :8081 | yes | OIDC | H2 in memory |
| `api` profile | `SPRING_PROFILES_ACTIVE=api` | API on :8081, monitoring on :8082 | no | OIDC | MySQL |
| `worker` profile | `SPRING_PROFILES_ACTIVE=worker` | monitoring only, on :8082 | yes | — | MySQL |
| Desktop (`local`) | `java -jar JTypoSquatting.jar` (`CombinedLauncher`) | API on 127.0.0.1:8081 | yes | none | H2 in memory |

| Property | Effect |
|----------|--------|
| `jtypo.security.mode` | `oidc` (default): login required on `/api/**`. `local`: no login, legacy endpoints enabled |
| `jtypo.worker.enabled` | Enables `ScanWorker`, `RetentionService` and scheduling (`SchedulingConfig`) |
| `jtypo.api.enabled` | `false` on workers: `ScanController`, `UserController`, `AccountController`, `ScreenshotController` are not loaded |

---

## 3. Components

### 3.1 Web frontend (`web/`)

- **Framework:** Next.js 16.4 (App Router, Cache Components, partial prefetching), React 19.3, TypeScript, Tailwind 4.
- **Pages:**
  - Public pages are prerendered statically (home, algorithms, FAQ, legal notice, terms, privacy), in `/fr` and `/en`.
  - The application (`/[locale]/app`, `/[locale]/app/scans/[id]`) is rendered on the client.
- **i18n:** `next-intl`. `src/proxy.ts` (the Next 16 equivalent of middleware) picks the locale prefix
  from `Accept-Language`.
- **Backend access:** same origin, through nginx in production and through `rewrites` in development
  (`BACKEND_URL`, default `http://localhost:8081`). `src/lib/api.ts`:
  - session cookie, CSRF token read from the `XSRF-TOKEN` cookie and sent in `X-XSRF-TOKEN`;
  - `EventSource` for the live results.
- **Results table:** virtualised (`@tanstack/react-virtual`); events are applied in batches every 250 ms.
  Tested with 50,000 rows.
- **Security headers:** static CSP set in `next.config.ts`. A nonce-based CSP would make every page dynamic.

### 3.2 Backend (`backend/`)

| Package | Responsibility | Main classes |
|---------|---------------|--------------|
| `config` | Security chains, OIDC client, scheduling | `SecurityConfig`, `LazyClientRegistrationRepository`, `JsonLogoutSuccessHandler`, `SchedulingConfig` |
| `scan` | Scans as jobs: API, queue, worker, SSE, shared cache | `ScanController`, `ScanService`, `ScanRepository`, `ScanWorker`, `ScanEventStreamer`, `DomainChecker`, `DomainCheckRepository`, `ScanResultRepository`, `UserRepository`, `ScanQueueMetrics` |
| `generator` | 21 generation algorithms | `Algorithm` (registry), `CharacterEdits`, `Keyboard`, `DotDash`, `Dash`, `Homoglyph`, `Misspell`, `Words`, `Suffixes`, `TLD`, `DomainParts` |
| `service` | Analysis of one domain | `DomainCheckService`, `PageAnalyzer`, `PlaywrightScreenshotService` |
| `security` | Outbound (anti-SSRF) policy | `OutboundPolicy`, `GuardedDnsResolver`, `BlockedDestinationException` |
| `abuse` | Rate limiting and quota errors | `RateLimitFilter`, `QuotaExceededException`, `QuotaExceptionHandler` |
| `storage` | Screenshots outside the database | `ScreenshotStore`, `S3ScreenshotStore`, `FileSystemScreenshotStore`, `ScreenshotController`, `ScreenshotGarbageCollector`, `StorageConfig` |
| `account` | GDPR export and account deletion | `AccountController`, `AccountService` |
| `retention` | Nightly purge | `RetentionService` |
| `persistence` | Stored form of domain names (punycode) | `DomainKeys` |
| `controller`, `db` | Legacy API of the desktop app (local mode only) and current user | `TypoSquattingController`, `DomainDataController`, `DatabaseService`, `UserController` |

### 3.3 Shared module (`shared/`)

DTOs used by the backend and the Swing client (`DomainResultDTO`, `DomainPageDTO`, `GenerationRequestDTO`,
`DomainStatus`, `TlsStatus`) and the domain name model (`DomainName`, validated with Guava `InternetDomainName`).

### 3.4 Desktop client (`frontend/`)

A Swing application (`JTypoFrame`) that uses the legacy endpoints (`/api/generate-and-check`,
`/api/data/**`, `/api/cancel*`). `CombinedLauncher` starts the backend in the same JVM with
`server.address=127.0.0.1`, `jtypo.security.mode=local` and raised quotas, then opens the window.
`:frontend:fatJar` builds the all-in-one `JTypoSquatting.jar`.

---

## 4. Scan Processing

### 4.1 Lifecycle

```
POST /api/scans ──► PENDING ──(claimed by a worker)──► RUNNING ──► COMPLETED
                       │                                  │  └──► FAILED
                       └──────── DELETE /api/scans/{id} ──┴────► CANCELLED
         RUNNING with a heartbeat older than 2 min ──► claimed again by another worker
```

1. **Creation (`ScanService`)**:
   - the domain is validated and stored in punycode;
   - the user row is upserted;
   - quotas are checked against the `scans` table: 2 active scans and 50 scans per 24 h by default.
     They are therefore global to all API instances.
2. **Claim (`ScanRepository.claim`)**:
   - every second, workers list claimable scans;
   - each worker then runs a conditional `UPDATE … WHERE status='PENDING' OR (status='RUNNING' AND heartbeat_at < ?)`;
   - only one worker can win, on MySQL as on H2. There is no `SKIP LOCKED` and no separate job table.
3. **Processing (`ScanWorker`)**:
   - generation through `JTypoSquatting` and the `Algorithm` registry, capped at 50,000 domains
     (`jtypo.quota.max-domains-per-scan`);
   - one virtual thread per domain;
   - each result is inserted in `scan_results` as soon as it is known.
4. **Heartbeat**:
   - every 5 s, `heartbeat_at` and `checked_domains` are updated;
   - if the update affects no row (scan cancelled or taken over), the worker stops its checks.
5. **Resumption**: a worker that takes over a scan skips the domains already in `scan_results`.

### 4.2 Shared cache (`DomainChecker`)

- **Cache hit:** a domain checked less than 24 h ago (`jtypo.cache.ttl-hours`), by any user, is read from
  `domain_checks`.
- **Forced check:** `forceRefresh` bypasses the cache.
- **Cache miss:** a real check updates the cache, and its screenshot goes to the `ScreenshotStore`.
- **History:** results are copied into `scan_results` as snapshots, so refreshing the cache never changes a
  past scan.

### 4.3 Live results (`ScanEventStreamer`)

`GET /api/scans/{id}/events` reads the database every 500 ms. The stream therefore works whichever
instance runs the scan.

| Event | Content |
|-------|---------|
| `result` | `ScanResultDTO`; the SSE `id` is the auto-increment id of the result |
| `progress` | `ScanDTO`, sent when status or counters change |
| `end` | Final `ScanDTO`, then the stream closes |

- **Resumption:** `Last-Event-ID` resumes after the last received result.
- **Keep-alive:** a comment is sent every 15 s.
- **Length:** a stream lasts at most 30 minutes; the browser then reconnects.
- **Headers:** responses carry `Cache-Control: no-cache, no-transform` and `X-Accel-Buffering: no`, so that
  Next.js and nginx neither compress nor buffer them.

---

## 5. Domain Analysis Engine

### 5.1 Generation

`JTypoSquatting` applies every value of the `Algorithm` enum to the base domain and removes duplicates
and the base domain itself. `DomainParts` splits names with the Public Suffix List (`co.uk` is one suffix).
Candidates that are not valid names are dropped; dot/dash variants must also end with a real public suffix.

| Family | Algorithms |
|--------|-----------|
| Characters | omission, repetition, changeOrder, addition, vowelSwap |
| Keyboard (QWERTY + AZERTY) | replacement, doubleReplacement |
| Dots and dashes | addDash, stripDash, missingDot, subdomain, changeDotDash |
| Look-alike characters | homoglyph (Latin look-alikes: accents, digits, `rn` for `m`; no Cyrillic) |
| Words | commonMisspelling, homophones, singularPluralize, numeralSwap |
| Extensions | wrongTld, addTld, wrongSld, addDynamicDns |

A scan of `example.com` generates about 1,800 domains, most of them TLD variants. Details:
[Technical reference](TECHNICAL_REFERENCE.md).

### 5.2 Checking one domain

```
DomainChecker ─► DomainCheckService (semaphore: 64 checks per instance)
                   └─► PageAnalyzer
                         ├─ Apache HttpClient 5 + GuardedDnsResolver (OutboundPolicy)
                         │    HTTPS with strict validation → on certificate error: TlsStatus + HTTP fallback
                         │    manual redirects (max 5, each hop re-checked), body ≤ 2 MB
                         ├─ JSoup: title, meta description/keywords/author/og:*
                         ├─ Lingua: language (12 languages)
                         └─ PlaywrightScreenshotService (2xx page with text)
```

- **Status:** HTTP 200 → `SUSPICIOUS`, other HTTP codes → `SAFE`, no response → `UNREACHABLE`.
- **TLS:** `TlsStatus` takes one of `VALID`, `EXPIRED`, `HOSTNAME_MISMATCH`, `UNTRUSTED`, `HANDSHAKE_FAILED`, `NONE`.
- **Internationalised domains:** they are contacted in their punycode form.
- **Timeouts:** connection 3 s, response 10 s; at most 4 connections per host and 200 in total.

### 5.3 Screenshots

`PlaywrightScreenshotService` captures pages with headless Chromium (Playwright 1.63):
- a bounded pool of browsers, 2 by default;
- a fresh `BrowserContext` per capture, with downloads and service workers disabled;
- every request issued by the page is checked by `OutboundPolicy`;
- PNG at 1280×800, with a 15 s timeout.

Screenshots are stored by `ScreenshotStore`, addressed by the SHA-256 of their content (`screenshots/<sha256>.png`):
- **S3** (`jtypo.storage.type=s3`), for the web service;
- **filesystem** (`filesystem`), for development and the desktop app.

They are served by `GET /api/screenshots/{sha256}` only to a user who owns a scan containing them.

---

## 6. Data Architecture

Flyway migrations, one set per database (`db/migration/mysql`, `db/migration/h2`):

| Migration | Content |
|-----------|---------|
| V1 | `users`, `scans`, `domain_checks`, `scan_results` |
| V2 | Spring Session tables (`SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`) |
| V3 | `domain_page_data` (legacy table of the desktop app) |
| V4 | Index on `scan_results.screenshot_key` |

```
users (id = OIDC subject) 1 ──── n scans (id UUID, status, counters, worker_id, heartbeat_at)
                                        1 ──── n scan_results (id auto-increment = SSE event id,
                                                               UNIQUE (scan_id, domain), snapshot)
domain_checks (domain PK): shared cache, latest check of each domain
```

- **Domain names:** stored in lower-case punycode with an `ascii_bin` collation in MySQL. MySQL's default
  `utf8mb4_0900_ai_ci` would treat `exämple.com` and `example.com` as equal. The Unicode form is kept for display.
- **Large or variable fields:** HTTP headers go in a JSON column (`CLOB` on H2). Screenshots are never stored
  in the database, only their key.
- **Deletions:** `ON DELETE CASCADE` from `users` to `scans` and from `scans` to `scan_results`.

---

## 7. Security Architecture

| Threat | Measure | Where |
|--------|---------|-------|
| Unauthenticated access | `/api/**` requires a session (401, no redirect); legacy endpoints only exist in local mode | `SecurityConfig` |
| Account takeover | OIDC authorization code with PKCE (S256). The OIDC discovery is lazy, so the app starts without the IdP. Session in an httpOnly cookie, `SameSite=Lax`, `Secure` behind HTTPS, stored by Spring Session | `SecurityConfig`, `LazyClientRegistrationRepository` |
| CSRF | `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header for modifying requests | `SpaCsrfTokenRequestHandler` |
| Access to another user's data | Ownership check on scans, results and SSE streams (403); screenshots only for owners (404) | `ScanService`, `ScreenshotController` |
| SSRF through generated domains | Private, loopback, link-local, CGNAT, reserved, multicast, NAT64/6to4/Teredo, ULA ranges refused. The client connects to the validated address. Ports 80/443 only. Redirects re-checked | `OutboundPolicy`, `GuardedDnsResolver` |
| Hostile pages | Fresh Chromium context per capture, page requests filtered. In production: unprivileged containers, read-only root, isolated network | `PlaywrightScreenshotService`, `deploy/` |
| Ignored certificate errors | No permissive TrustManager. Certificate problems are reported as `TlsStatus` | `PageAnalyzer` |
| Abuse | 120 requests/min per user (per IP when anonymous). 2 simultaneous scans and 50 per 24 h. 50,000 domains per scan. 64 outbound checks per instance. Identifiable User-Agent | `RateLimitFilter`, `ScanService`, `DomainCheckService` |
| Browser-side attacks | Strict CSP on the API (`default-src 'none'`), static CSP on the frontend, HSTS, `Referrer-Policy`, `Permissions-Policy`, no CORS (same origin) | `SecurityConfig`, `next.config.ts` |
| Logout | `POST /logout` returns the IdP end-session URL as JSON; the browser navigates to it | `JsonLogoutSuccessHandler` |

---

## 8. Concurrency and Limits

| Limit | Default | Property |
|-------|---------|----------|
| Scans processed at the same time per worker | 4 | `jtypo.worker.max-concurrent-scans` |
| Outbound checks at the same time per instance | 64 | `jtypo.analysis.max-concurrent-checks` |
| Browsers per instance | 2 | `jtypo.screenshot.max-concurrent` |
| Domains per scan | 50,000 | `jtypo.quota.max-domains-per-scan` |
| Worker heartbeat / takeover delay | 5 s / 2 min | `jtypo.worker.heartbeat-interval-ms` / `stale-after-ms` |
| Queue polling | 1 s | `jtypo.worker.poll-interval-ms` |

Scans, checks and SSE streams run on virtual threads (Java 21).

---

## 9. Operations

- **Monitoring:** Actuator on port 8082 for the `api` and `worker` profiles. `health` (with `liveness` and
  `readiness` probes), `info` and `prometheus` are exposed; nginx never routes `/actuator`. Business metrics:
  `jtypo_scans_submitted_total`, `jtypo_scans_finished_total{status}`, `jtypo_scan_duration_seconds`,
  `jtypo_scans_queue{status}`, `jtypo_checks_total{source}`, `jtypo_check_duration_seconds`,
  `jtypo_screenshots_total{outcome}`, `jtypo_quota_rejections_total{type}`, `jtypo_sse_streams`,
  `jtypo_retention_deleted_total{type}`. Alert rules are in `deploy/prometheus/alerts.yml`.
- **Logs:** JSON (ECS) in the containers (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`).
- **Retention:** every night at 03:30, on workers, `RetentionService` deletes:
  - finished scans after 90 days;
  - cached checks after 30 days;
  - screenshots that are no longer referenced (`ScreenshotGarbageCollector`).
- **GDPR:**
  - `GET /api/me/export` exports all the user's data;
  - `DELETE /api/me` deletes the user, their scans and their unshared screenshots, and ends their other
    sessions; the frontend then logs out.
- **Backups:** `deploy/backup/` contains the dump, restore and restore-test scripts for MySQL.
- **Containers:**

  | Image | Base |
  |-------|------|
  | `api` | Temurin 21 JRE |
  | `worker` | `mcr.microsoft.com/playwright/java:v1.63.0-noble` |
  | `web` | Node 22, Next.js standalone |
  | nginx | `deploy/nginx/jtyposquatting.conf` |

  The compose file is `deploy/docker-compose.prod.yml`, with three networks:
  - `edge`: nginx, web, API;
  - `data`: internal network for the API, workers and MySQL;
  - `egress`: Internet access for the workers only.

---

## 10. Technology Stack

| Layer | Technologies |
|-------|-------------|
| Frontend | Next.js 16.4, React 19.3, TypeScript 5.9, Tailwind 4, next-intl 4, TanStack Virtual, Playwright Test 1.63 |
| Backend | Java 21 (virtual threads), Spring Boot 3.5 on Jetty, Spring Security 6.5 (OAuth2 client), Spring Session JDBC |
| Analysis | Apache HttpClient 5.6, JSoup, Lingua, Guava (`InternetDomainName`), Playwright for Java 1.63 |
| Data | MySQL 8.4 (production), H2 (desktop, tests), Flyway 11, AWS SDK v2 (S3) |
| Limits | Bucket4j 8, Guava caches |
| Operations | Micrometer + Prometheus, Docker, nginx, GitHub Actions (tests, end-to-end tests, images on GHCR) |
| Desktop | Swing |
| Build | Gradle 9 (multi-module `shared`, `backend`, `frontend`), npm (`web`) |

---

## 11. Architecture Decisions

| ID | Decision | Status | Rationale / consequences |
|----|----------|--------|--------------------------|
| AD-001 | Java 21 virtual threads | Active | One virtual thread per checked domain; I/O-bound work |
| AD-002 | Client-server with SSE | Active | Now one SSE stream per scan, read from the database (resumable) |
| AD-003 | H2 embedded database | **Superseded by AD-008** | Kept for the desktop app and fast tests |
| AD-004 | JavaFX WebView for screenshots | **Superseded by AD-010** | Needed a display (Xvfb), not isolatable |
| AD-005 | All-in-one fat JAR | Active (desktop only) | The web service is delivered as container images |
| AD-006 | Swing UI | **Superseded by AD-007** for the web service | Still used by the desktop app; its removal is pending a product decision |
| AD-007 | Next.js frontend, same origin as the API | Active | SEO for public pages, no CORS; SSE goes directly to Spring, not through Next |
| AD-008 | MySQL 8.4 + Flyway, one migration set per database | Active | Domain keys in punycode with `ascii_bin` collation |
| AD-009 | Spring as OAuth2 client (BFF), session cookie | Active | `EventSource` cannot send an `Authorization` header |
| AD-010 | Headless Chromium (Playwright) in isolated workers | Active | Faithful rendering, every page request filtered |
| AD-011 | `scans` table as job queue, claim by conditional `UPDATE` | Active | Portable (H2/MySQL), no extra broker; a message broker can come later |
| AD-012 | Content-addressed screenshots on S3-compatible storage | Active | Deduplication, no BLOBs in the database |
| AD-013 | Separate `api` and `worker` roles from one artifact | Active | Independent scaling; workers expose monitoring only |
| AD-014 | Static CSP on the frontend | Active | A nonce-based CSP is incompatible with prerendering |

---

## 12. Source Layout

```
JTypoSquatting/
├── backend/       Spring Boot: API, workers, generators, analysis engine, Dockerfile (api, worker)
│   └── src/main/resources/db/migration/{mysql,h2}/   Flyway migrations
├── shared/        DTOs and DomainName
├── frontend/      Swing client and CombinedLauncher (desktop)
├── web/           Next.js frontend, Playwright end-to-end tests, Dockerfile
├── deploy/        Production compose, nginx, Prometheus, Keycloak realm (dev), backup scripts
├── doc/           Documentation
└── docker-compose.yml   Development services (MySQL, Keycloak)
```
