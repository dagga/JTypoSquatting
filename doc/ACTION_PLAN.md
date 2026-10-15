# Action Plan — JTypoSquatting: towards a public multi-user web service

## 1. Introduction

This document replaces the initial action plan, which came from the code audit ([`AUDIT.md`](AUDIT.md)).

JTypoSquatting is moving from a single-user desktop application (all-in-one Spring Boot + Swing JAR) to a **public website used by several users at the same time**. The Java backend is kept; the Swing interface is replaced by a **Next.js** frontend.

This change of target turns several weaknesses that were acceptable locally (API without authentication, global cancellation, hostile pages executed in the API process) into **blockers for going live**.

## 2. Goals

- Open the service to the public without exposing the infrastructure (SSRF, execution of hostile content, abuse).
- Support several simultaneous users, with isolated and persistent scans that can be cancelled individually.
- Offer a modern web interface (indexed public pages + real-time application).
- Finish cleaning up the technical debt identified by the audit.

## 3. Target architecture

```
Browser ─► Reverse proxy (Traefik / nginx, TLS) ─┬─ /        ─► Next.js (static public pages + client application)
                                                 └─ /api/*   ─► Spring Boot API ─┬─► MySQL 8.4
                                                                                 ├─► S3 / MinIO object storage (screenshots)
                                                                                 └─► Job table ─► Analysis workers
                                                                                                 (Playwright + headless Chromium,
                                                                                                  isolated network, Internet egress only)
```

| Topic | Choice | Rationale |
|---|---|---|
| Frontend | Next.js (App Router, TypeScript) | Public pages with SEO (static generation) and the client application in one project |
| Real-time stream | SSE **directly** to Spring through the reverse proxy | Avoid the buffering and timeouts of a Next.js proxy on long-lived streams |
| Authentication | OIDC (Keycloak or a managed IdP); **Spring as the OAuth2 client (BFF pattern)**, session in an `httpOnly` cookie | `EventSource` cannot send an `Authorization` header: a same-origin session cookie is the natural solution for SSE |
| Sessions | Spring Session JDBC (MySQL) | Sessions shared between several API instances |
| Database | **MySQL 8.4 LTS** (InnoDB, `utf8mb4`) + Flyway | See the constraints in §3.1 |
| Screenshots | Playwright for Java + headless Chromium | Replaces JavaFX WebView: no X11/Xvfb dependency, can be isolated, faithful rendering |
| Job queue | MySQL table + `SELECT … FOR UPDATE SKIP LOCKED` | Enough for V1; RabbitMQ can be considered if the load requires it |

### 3.1 MySQL-specific constraints

- **Collation of domain names:** the default collation `utf8mb4_0900_ai_ci` ignores case **and accents**: `exämple.com` and `example.com` are equal under it, which would cause unique index collisions and false cache hits for homoglyph variants.
  - Technical key: **ASCII/punycode** form (`IDN.toASCII`) in `VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin`.
  - Unicode form stored separately, for display only (`utf8mb4_bin`).
- **Page metadata** (`metadata` map): `JSON` column.
- **Screenshots**: never as a `BLOB`; only the key of the S3 object is stored.
- **Tests**: MySQL with Testcontainers (the MySQL compatibility mode of H2 is not faithful).
- **Driver / migrations**: `com.mysql:mysql-connector-j`, Flyway with the `flyway-mysql` module.

## 4. Status of the previous plan

| Ref. | Action | Status | Note |
|---|---|---|---|
| 3.1 | Single JDBC connection → `JdbcTemplate` | ✅ Done | `DatabaseService` uses `JdbcTemplate` |
| 3.2 | JavaFX blocking in `PageAnalyzer` | ✅ Done | No longer relevant with Playwright (P1.3) |
| 3.3 | Fix `LogTailer` | ✅ Done | Swing component, to be removed eventually (P3.7) |
| 3.4 | Synchronise SSE sends | ✅ Done | `synchronized (emitter)` in place |
| 3.5 | Lost variants in `KeysValuesSwap` | ✅ Done | `KeysValuesSwapMulti` (`Map<String, List<String>>`) |
| 4.1 | `DomainStatus` enum | ✅ Done | |
| 4.2 | Fix `Dash` / `DomainName` | ✅ Done | Validation through `InternetDomainName` |
| 4.3 | Warning before opening a domain | ⏳ To do | Moved to the web frontend (P3.5) |
| 4.4 | Isolated tests (WireMock) | 🟡 Partial | WireMock in `PageAnalyzerUnitTest`; to be extended (P2.7) |
| 5.1 | Remove `json-simple` | 🟡 Partial | No longer used in the code; `lib/json-simple-1.1.1.jar` to be deleted |
| 5.2 | Dead code `GenerationRequestDTO` | ⏳ To do | To be reused as the body of `POST /api/scans` (P2.2) |
| 5.3 | Leftover Tomcat properties | ⏳ To do | `application-production.properties` lines 34-35 |
| 5.4 | `System.out.println` → logger | 🟡 Partial | 5 occurrences left |
| 5.5 | 17 missing algorithms | ⏳ To do | Kept in Phase 0 |

## 4bis. Progress (2026-10-14, branch `feature/web-public`)

| Action | Status | Notes |
|---|---|---|
| P0.1 Remaining audit actions | ✅ | |
| P0.2 Missing algorithms | ✅ | The 21 algorithms; `Algorithm` enum |
| P0.3 New algorithms | ⏳ | Combosquatting, insertion, bitsquatting, homoglyphs from other alphabets, extension inside the label: see the README ("Algorithms still to develop") |
| P1.1 Authentication | ✅ | Lazy OIDC discovery; `local` mode for the desktop application |
| P1.2 Anti-SSRF | ✅ | + fix: internationalised domains converted to punycode before the request |
| P1.3 Playwright | ✅ | Chromium in the worker container; network isolation in the compose file (P4.1) |
| P1.4 TLS validation | ✅ | |
| P1.5 Anti-abuse | 🟡 | Rate limiting, quotas, caps (50,000 domains per scan), User-Agent: done. Sign-up captcha: to be enabled in Keycloak (keys of the domain) |
| P1.6 Headers, CORS | ✅ | |
| P2.1 MySQL + Flyway | ✅ | Migrations per database vendor (MySQL, H2 for the desktop and the tests) |
| P2.2 Persistent jobs | ✅ | **Deviation**: the `scans` table is the queue (claimed with a conditional `UPDATE`, portable) instead of a `scan_jobs` table with `SKIP LOCKED` |
| P2.3 SSE per scan, cancellation | ✅ | Legacy endpoints loaded in desktop mode only |
| P2.4 Shared cache | ✅ | |
| P2.5 Object storage | ✅ | Any S3 service (tests with S3Mock: the MinIO images are no longer published) or file system |
| P2.6 API / workers | ✅ | Workers keep an HTTP server limited to monitoring (P4.3) |
| P2.7 Backend tests | ✅ | `./gradlew test` without Internet; network tests with `-Dinclude.network=true` |
| P3.1 Next.js and public pages | ✅ | Next.js 16.4, static prerendering |
| P3.2 Frontend authentication | ✅ | Logout returns the provider URL as JSON |
| P3.3 Scan application | ✅ | Tested with 50,000 results |
| P3.4 Internationalisation | ✅ | Delivered with P3.1 (next-intl) |
| P3.5 Safe viewing | ✅ | Delivered with P3.3 |
| P3.6 End-to-end tests | ✅ | Playwright with a simulated backend, in CI |
| P3.7 Removal of Swing | ⏸️ | **Not done**: product decision (removal of the desktop application and its installers) to be validated |
| P4.1 Containers, reverse proxy | 🟡 | Images, nginx and compose validated with Podman. Remaining: TLS, host firewall rules for the workers' network |
| P4.2 CI/CD | 🟡 | Tests, and images published to GHCR; deployment to be scripted for the hosting |
| P4.3 Observability | ✅ | Prometheus, JSON logs, alert rules; collector and Prometheus to be deployed |
| P4.4 Backups, retention | ✅ | Scripts tested; scheduling to be set up on the host |
| P4.5 Compliance | 🟡 | Account export and deletion done; `doc/COMPLIANCE.md` and legal texts to be validated by the legal team |

Go-live criteria (§ 10): covered technically, except the legal texts (legal team), actual monitoring
(Prometheus deployment) and scheduled backups on the host.

## 5. Phase 0 — Finish the cleanup

### P0.1 Close the remaining audit actions

- **Description:** Delete `lib/json-simple-1.1.1.jar`; delete the `server.tomcat.*` properties; replace the 5 remaining `System.out.println` with SLF4J.
- **Acceptance criterion:** `grep` finds no `json-simple`, `server.tomcat` nor `System.out.println` in `src/main`.
- **Owner:** Backend developer
- **Estimate:** 0.5 d
- **Due date:** [Date]

### P0.2 Implement the missing algorithms

- **Description:** Implement the 17 algorithms declared in `Const.ALGO_NAME_LIST` (`omission`, `repetition`, `vowelSwap`, `homophones`, `numeralSwap`, etc.) or remove those that will not be delivered. Each algorithm is covered by unit tests.
- **Acceptance criterion:** no orphan algorithm name; test coverage on every generator.
- **Owner:** Backend developer
- **Estimate:** 5-8 d (can be done in parallel with the following phases)
- **Due date:** [Date]

### P0.3 Additional algorithms

- **Description:** Cover the techniques of the reference tools (dnstwist, URLCrazy) that the generator
  lacks: combosquatting (brand + phishing words), insertion of a neighbouring key inside the label,
  bitsquatting, homoglyphs from other alphabets (Cyrillic, Greek) and over the whole label, extension
  glued to the label. Examples in the README.
- **Acceptance criterion:** each algorithm declared in `Algorithm`, tested, documented with an example;
  volume per scan under the 50,000-domain cap.
- **Owner:** Backend developer
- **Estimate:** 4-6 d (including the combosquatting dictionary)
- **Due date:** [Date]

## 6. Phase 1 — Security and foundations (blocking before opening to the public)

### P1.1 Authentication and authorisation

- **Description:** Set up an OIDC provider; configure Spring Security as an OAuth2 client (login, logout, `httpOnly`, `Secure`, `SameSite=Lax` session cookie); protect every `/api/**` route except health and public content; CSRF protection on state-changing requests.
- **Acceptance criterion:** every unauthenticated call to `/api/scans/**` returns 401; a user can neither read nor cancel another user's scan (403).
- **Owner:** Backend developer
- **Estimate:** 4-5 d
- **Due date:** [Date]

### P1.2 Anti-SSRF protection of the analysis engine

- **Description:** Before any connection (HTTP analysis and screenshot):
  - Resolve the DNS name, then reject private, loopback, link-local, reserved and cloud metadata ranges (IPv4 and IPv6, including `169.254.169.254`, `fd00::/8`, `::ffff:` mapped addresses).
  - Connect to the validated IP (against DNS rebinding).
  - Allow ports 80/443 only and validate every redirect hop again (bounded number of redirects).
- **Acceptance criterion:** automated tests covering a domain that resolves to each forbidden IP category, a redirect to an internal IP, and a rebinding case.
- **Owner:** Backend developer
- **Estimate:** 3-4 d
- **Due date:** [Date]

### P1.3 Replace JavaFX WebView with isolated Playwright

- **Description:** Implement `ScreenshotService` with Playwright for Java (headless Chromium). Workers run in dedicated containers: network without internal access (Internet egress only), CPU/memory limits, timeout per page, fresh browser context per domain, no downloads. Remove the JavaFX dependencies and the related `--add-opens` / Xvfb options.
- **Acceptance criterion:** screenshots work without an X server; a worker can reach neither the API, nor MySQL, nor the internal network.
- **Owner:** Backend developer / DevOps
- **Estimate:** 4-5 d
- **Due date:** [Date]

### P1.4 Controlled TLS validation (audit SEC-01)

- **Description:** Remove the permissive `TrustManager` and `HostnameVerifier` from `PageAnalyzer`. Connect with standard validation; on failure, record the certificate status (valid / self-signed / expired / wrong name) as an **analysis signal** instead of hiding the error.
- **Acceptance criterion:** no permissive `TrustManager` in the code; TLS status visible in the results.
- **Owner:** Backend developer
- **Estimate:** 1-2 d
- **Due date:** [Date]

### P1.5 Anti-abuse

- **Description:** Rate limiting per user and per IP (Bucket4j); quotas (scans per day, domains per scan, simultaneous scans); politeness per target domain (bounded concurrency and frequency); captcha at sign-up; identifiable User-Agent with an information page.
- **Acceptance criterion:** exceeding a quota → 429 with an explicit message; quota metrics exposed.
- **Owner:** Backend developer
- **Estimate:** 2-3 d
- **Due date:** [Date]

### P1.6 Security headers and CORS

- **Description:** Serve the frontend and the API on the same origin through the reverse proxy, and remove the CORS configuration of `WebConfig` (currently inconsistent: origin `:8080`, API `:8081`). Add CSP, HSTS, `X-Content-Type-Options`, `Referrer-Policy`.
- **Acceptance criterion:** grade A on a header scanner (e.g. securityheaders.com).
- **Owner:** Backend developer / DevOps
- **Estimate:** 1 d
- **Due date:** [Date]

## 7. Phase 2 — Execution model and data

### P2.1 Migration H2 → MySQL 8.4

- **Description:** Versioned Flyway schema: `users`, `scans`, `scan_results`, `domain_checks` (shared cache), `scan_jobs`, Spring Session tables. Follow the constraints of §3.1 (punycode + `ascii_bin`, `JSON`, no `BLOB`). Sized HikariCP pool. Remove H2 and the `typosquatting_db.mv.db` file from the repository.
- **Acceptance criterion:** application started on MySQL with Docker Compose; Testcontainers integration tests green.
- **Owner:** Backend developer
- **Estimate:** 3-4 d
- **Due date:** [Date]

### P2.2 Scans as persistent jobs

- **Description:** `POST /api/scans` (body: `GenerationRequestDTO`, which resolves the former action 5.2) creates the scan and returns a `scanId`. Workers take tasks with `SELECT … FOR UPDATE SKIP LOCKED`. Lifecycle: `PENDING → RUNNING → COMPLETED | CANCELLED | FAILED`. A scan survives closing the browser tab and restarting an instance.
- **Acceptance criterion:** restarting a worker during a scan causes neither loss nor double processing.
- **Owner:** Backend developer
- **Estimate:** 4-5 d
- **Due date:** [Date]

### P2.3 SSE stream per scan and targeted cancellation

- **Description:** `GET /api/scans/{id}/events` with resumption through `Last-Event-ID`; `DELETE /api/scans/{id}` cancels **only** that scan, after checking the owner. Remove `/api/cancel` and `/api/cancel-and-clear`, which currently act on every session and empty the whole database.
- **Acceptance criterion:** two simultaneous users; cancelling one does not affect the other; SSE reconnection without lost events.
- **Owner:** Backend developer
- **Estimate:** 2-3 d
- **Due date:** [Date]

### P2.4 Shared cache of checks

- **Description:** Reuse the result of a domain checked recently (configurable validity, e.g. 24 h), whoever the user, with the option to force a new check.
- **Acceptance criterion:** two successive scans of the same domain trigger only one series of outbound requests within the validity window.
- **Owner:** Backend developer
- **Estimate:** 1-2 d
- **Due date:** [Date]

### P2.5 Object storage of screenshots

- **Description:** Store screenshots in S3 / MinIO; expose `GET /api/screenshots/{id}` (access control, or a time-limited signed URL). SSE events no longer carry a base64 `byte[]` but a URL.
- **Acceptance criterion:** average size of an SSE event < 2 KB.
- **Owner:** Backend developer
- **Estimate:** 2 d
- **Due date:** [Date]

### P2.6 Separate API and workers

- **Description:** Same code base, two Spring profiles (`api`, `worker`) that can be deployed and scaled independently.
- **Acceptance criterion:** a worker instance can be added without redeploying the API.
- **Owner:** Backend developer / DevOps
- **Estimate:** 1-2 d
- **Due date:** [Date]

### P2.7 Backend test strategy

- **Description:** Extend WireMock to every outbound HTTP call (end of the former action 4.4); Testcontainers MySQL and MinIO for integration; multi-user concurrency tests.
- **Acceptance criterion:** `./gradlew test` passes without Internet access.
- **Owner:** Backend developer
- **Estimate:** 3 d
- **Due date:** [Date]

## 8. Phase 3 — Next.js frontend

### P3.1 Setup and public pages

- **Description:** Next.js project (App Router, TypeScript, ESLint); statically generated public pages: home, how the algorithms work, FAQ, legal notice, terms of use, privacy policy. SEO metadata, sitemap.
- **Owner:** Frontend developer
- **Estimate:** 4-5 d
- **Due date:** [Date]

### P3.2 Authentication integration

- **Description:** Login / logout / sign-up flows redirecting to the Spring endpoints (BFF); current user fetched with `GET /api/me`; protected application routes.
- **Owner:** Frontend developer
- **Estimate:** 2 d
- **Due date:** [Date]

### P3.3 Scan application

- **Description:**
  - Form to start a scan.
  - Virtualised real-time table (TanStack Table + virtualisation) fed by SSE with automatic reconnection.
  - Counters (generated, active, unreachable), screenshot preview, details of a domain, history of the user's scans, cancellation, CSV/JSON export.
- **Acceptance criterion:** functional parity with the Swing interface; smooth with 5,000 rows.
- **Owner:** Frontend developer
- **Estimate:** 8-10 d
- **Due date:** [Date]

### P3.4 Internationalisation

- **Description:** French/English with `next-intl`, on the public pages and in the application.
- **Owner:** Frontend developer
- **Estimate:** 1-2 d
- **Due date:** [Date]

### P3.5 Safe viewing of domains (former action 4.3 / audit SEC-02)

- **Description:** Intermediate warning page before opening an analysed domain (risk of malicious content); links with `rel="noopener noreferrer"`; the screenshot is offered as a safe alternative.
- **Owner:** Frontend developer
- **Estimate:** 0.5 d
- **Due date:** [Date]

### P3.6 End-to-end tests

- **Description:** Playwright scenarios: login, starting a scan, receiving the stream, cancellation, export, quotas.
- **Owner:** Frontend developer
- **Estimate:** 2-3 d
- **Due date:** [Date]

### P3.7 Removal of the Swing interface

- **Description:** Once parity is validated, delete the Swing `frontend` module, `CombinedLauncher`, `LogTailer` and the related jpackage tasks.
- **Owner:** Backend developer
- **Estimate:** 1 d
- **Due date:** [Date]

## 9. Phase 4 — Infrastructure, operations and compliance

### P4.1 Containers and reverse proxy

- **Description:** Docker images (API, worker, Next.js); development `docker compose` (MySQL, MinIO, Keycloak); reverse proxy with TLS. SSE configuration: `proxy_buffering off`, long read timeouts.
- **Owner:** DevOps
- **Estimate:** 2-3 d
- **Due date:** [Date]

### P4.2 CI/CD

- **Description:** Extend the existing GitHub Actions workflows: build and tests (Testcontainers), frontend lint and E2E tests, build and publication of the images, deployment.
- **Owner:** DevOps
- **Estimate:** 2 d
- **Due date:** [Date]

### P4.3 Observability

- **Description:** Spring Actuator + Micrometer (Prometheus): scan duration, job queue size, errors, quotas reached, worker resource usage. Centralised JSON logs (replaces the Swing log viewer). Alerts on queue saturation and error rate.
- **Owner:** DevOps
- **Estimate:** 2 d
- **Due date:** [Date]

### P4.4 Backups and retention

- **Description:** Automated MySQL backups with restore tests; scheduled purge of scans and screenshots beyond the defined retention period.
- **Owner:** DevOps
- **Estimate:** 1 d
- **Due date:** [Date]

### P4.5 Compliance

- **Description:** GDPR (record of processing activities, retention periods, rights of access and account deletion); terms of use with an acceptable use policy; reporting and takedown procedure; reputation monitoring of the workers' egress IPs.
- **Owner:** Legal / Product Owner
- **Estimate:** to be defined
- **Due date:** [Date]

## 10. Criteria for going live to the public (go / no-go)

- [ ] Mandatory authentication and per-user isolation of scans (P1.1, P2.3)
- [ ] SSRF protection tested, including redirects and DNS rebinding (P1.2)
- [ ] Workers isolated from the internal network (P1.3)
- [ ] No permissive `TrustManager` (P1.4)
- [ ] Quotas and rate limiting active (P1.5)
- [ ] MySQL backups tested (P4.4)
- [ ] Terms of use, privacy policy and legal notice published (P4.5)
- [ ] Monitoring and alerts operational (P4.3)

## 11. Estimate and sequencing

| Phase | Effort (1 person) | Dependencies |
|---|---|---|
| Phase 0 — Cleanup | 1 wk (+ algorithms in parallel) | — |
| Phase 1 — Security and foundations | 3-4 wk | — |
| Phase 2 — Execution and data | 3-4 wk | P1.1 |
| Phase 3 — Next.js frontend | 4-5 wk | P3.1 can start immediately; P3.3 depends on P2.2-P2.5 |
| Phase 4 — Infrastructure and compliance | 1-2 wk (+ legal) | P4.1 from the start of Phase 2 |
| **Total** | **≈ 12-16 weeks** (≈ 7-9 with one backend and one frontend developer in parallel) | |

## 12. Main risks

| Risk | Impact | Mitigation |
|---|---|---|
| Exploitation of the analysis engine (SSRF, browser vulnerability) | Critical | P1.2, P1.3, regular Chromium updates |
| Abuse of the service (mass scanning, free proxy) | High | P1.5, terms of use, monitoring |
| Workers' egress IPs blocklisted | Medium | Politeness per target, identifiable User-Agent, dedicated egress IP |
| Memory cost of Chromium (≈ 200-500 MB per context) | Medium | Bounded browser pool, scaling the workers |
| Collisions of homoglyph domains in the database | Medium | Punycode + binary collation (§3.1) |
| Scope creep (algorithms, features) | Medium | Swing parity as the V1 scope, the rest in V2 |

---

**Revision:** 2026-10-14
**Owner:** [Name]
