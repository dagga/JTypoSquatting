# JTypoSquatting — Deployment Guide

**Version:** 2.0-alpha1

## Table of Contents

1. [System Requirements](#1-system-requirements)
2. [Installation Options](#2-installation-options)
3. [Building from Source](#3-building-from-source)
4. [Running the Application](#4-running-the-application)
5. [Configuration](#5-configuration)
5bis. [Web Service Deployment (API and workers)](#5bis-web-service-deployment-api-and-workers)
6. [Native Installers (desktop)](#6-native-installers-desktop)
7. [CI/CD with GitHub Actions](#7-cicd-with-github-actions)
8. [Troubleshooting](#8-troubleshooting)
9. [Uninstallation](#9-uninstallation)
- [Appendix A: File Locations](#appendix-a-file-locations)
- [Appendix B: Port Usage](#appendix-b-port-usage)
- [Appendix C: Quick Reference](#appendix-c-quick-reference)

---

## 1. System Requirements

| Use | Requirements |
|-----|--------------|
| Desktop application | JDK or JRE 21+, a display, Internet access. Headless Chromium for screenshots, installed once. |
| Development (web service) | JDK 21–24 (Gradle 9.0 does not run on Java 25), Node.js 22, Docker or Podman |
| Production (web service) | Docker Engine with Compose (or equivalent orchestrator); an OpenID Connect provider; an S3-compatible storage; a DNS name with TLS |

Sizing guidelines for production:

- **Worker**: 2 CPUs and 3 GB of memory, of which 1 GB is shared memory for Chromium. Scale the number of
  workers with the load.
- **API instance**: 1 CPU and 1 GB of memory.
- **MySQL 8.4**: the results of 50 scans of about 1 800 domains take a few hundred MB, because the
  screenshots are in S3.

---

## 2. Installation Options

| Option | For | How |
|--------|-----|-----|
| All-in-one JAR | One analyst, desktop | `java -jar JTypoSquatting.jar` (§ 4.1) |
| Native installer | One analyst, without managing Java | jpackage image, MSI or EXE (§ 6) |
| Web service | Several users | Containers: `deploy/docker-compose.prod.yml` (§ 5bis) |

The GitHub releases (tags `v*`) provide the all-in-one JAR. The container images are published to
`ghcr.io/<owner>/jtyposquatting-{api,worker,web}` (§ 7).

---

## 3. Building from Source

```bash
git clone https://github.com/dagga/JTypoSquatting.git
cd JTypoSquatting
```

| Command | Result |
|---------|--------|
| `./gradlew build` | Compiles and tests every module |
| `./gradlew :backend:bootJar` | Backend (API and worker): `backend/build/libs/jtyposquatting-backend.jar` |
| `./gradlew :frontend:fatJar` | Desktop all-in-one JAR: `frontend/build/libs/JTypoSquatting.jar`, about 283 MB (backend, Swing client, Playwright driver) |
| `./gradlew :frontend:copyFatJar` | Same, copied to the project root |
| `./gradlew :backend:installPlaywrightBrowsers` | Installs headless Chromium in `~/.cache/ms-playwright` (`-PwithDeps` also installs the system libraries) |
| `cd web && npm ci && npm run build` | Frontend build (`web/.next/standalone`) |
| `docker build -f backend/Dockerfile --target api .` | API image (`--target worker` for the worker) |
| `docker build web` | Frontend image |

`gradle.properties` may set `org.gradle.java.home` to the JDK of a developer machine. Elsewhere, override it
with `-Dorg.gradle.java.home="$JAVA_HOME"`.

---

## 4. Running the Application

### 4.1 Desktop application

```bash
java -jar JTypoSquatting.jar
```

- The JAR starts the backend in **local** mode: the API listens on 127.0.0.1:8081, needs no login, and
  keeps the scan quotas out of the way.
- It then opens the Swing window.
- The data is kept in memory (H2) and lost when the application stops.
- Screenshots are stored in `${java.io.tmpdir}/jtyposquatting`.

Without a Chromium build, scans run but take no screenshot. Install it once, from the JAR:

```bash
java -cp JTypoSquatting.jar com.microsoft.playwright.CLI install chromium
```

### 4.2 Web service, development

```bash
docker compose up -d keycloak                     # OpenID Connect provider on :8180 (alice / alice)
FORWARD_HEADERS_STRATEGY=framework ./gradlew :backend:bootRun   # API + worker on :8081
cd web && npm run dev                              # http://localhost:3000
```

Without a profile, the backend:

- runs the API and the worker in a single process;
- uses an in-memory H2 database;
- stores screenshots on disk.

To use MySQL, run `docker compose up -d mysql` and set
`DB_URL=jdbc:mysql://localhost:3306/jtypo DB_USER=jtypo DB_PASSWORD=jtypo-dev`.

### 4.3 Web service, production

See [§ 5bis](#5bis-web-service-deployment-api-and-workers).

### 4.4 JVM options

The container images run Java with `-XX:MaxRAMPercentage=75`. For a JAR started by hand, size the heap
for the number of simultaneous checks, for example `java -Xmx2g -jar …`.

---

## 5. Configuration

The backend reads `backend/src/main/resources/application.properties`. Every property can be overridden:

- **On the command line**: `--jtypo.quota.scans-per-day=100`.
- **As an environment variable**: dots and dashes become underscores, in upper case, e.g.
  `JTYPO_QUOTA_SCANSPERDAY` or `JTYPO_QUOTA_SCANS_PER_DAY`.

Some properties also read a short variable directly (`DB_URL`, `S3_BUCKET`…).

### 5.1 Main properties

| Property (environment variable) | Default | Description |
|-------------------------------|---------|-------------|
| `jtypo.security.mode` | `oidc` | `oidc` (web service) or `local` (desktop, no login, set by the all-in-one JAR) |
| `jtypo.oidc.issuer-uri` (`OIDC_ISSUER_URI`) | `http://localhost:8180/realms/jtypo` | OpenID Connect issuer, discovered at the first login |
| `jtypo.oidc.client-id` / `client-secret` (`OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET`) | `jtypo-web` / development secret | Confidential client |
| `jtypo.security.post-login-url` | `/app` | Page reached after login |
| `server.servlet.session.cookie.secure` (`SESSION_COOKIE_SECURE`) | `false` | `true` behind HTTPS (set in the API image) |
| `server.forward-headers-strategy` (`FORWARD_HEADERS_STRATEGY`) | `none` | `framework` behind a reverse proxy |
| `spring.datasource.url` (`DB_URL`, `DB_USER`, `DB_PASSWORD`) | H2 in memory | `jdbc:mysql://host:3306/jtypo` in production |
| `jtypo.storage.type` (`STORAGE_TYPE`) | `filesystem` | `filesystem` or `s3` (see `S3_*` in § 5bis) |
| `jtypo.storage.directory` (`STORAGE_DIRECTORY`) | `${java.io.tmpdir}/jtyposquatting` | Screenshot directory of the `filesystem` storage |
| `jtypo.quota.max-concurrent-scans` | 2 | Simultaneous scans per user |
| `jtypo.quota.scans-per-day` | 50 | Scans per user over 24 hours |
| `jtypo.quota.max-domains-per-scan` | 50 000 | Generated domains checked per scan |
| `jtypo.rate-limit.requests-per-minute` | 120 | API requests per minute and per user (per IP when anonymous) |
| `jtypo.analysis.max-concurrent-checks` | 64 | Simultaneous outbound checks per instance |
| `jtypo.cache.ttl-hours` | 24 | A domain checked more recently is taken from the shared cache |
| `jtypo.outbound.user-agent` | `Mozilla/5.0 (compatible; JTypoSquatting/2.0)` | User-Agent of the checks and screenshots; add a contact URL |
| `jtypo.outbound.allow-private-addresses` | `false` | Never `true` on a public deployment (anti-SSRF) |
| `jtypo.outbound.allowed-ports` | `80,443` | Ports that checks may contact |
| `jtypo.screenshot.enabled` / `chromium-sandbox` | `true` / `true` | Screenshots; Chromium sandbox (`false` only inside an isolated container) |
| `jtypo.screenshot.timeout-ms` / `max-concurrent` | 15000 / 2 | Capture timeout; browsers per instance |
| `jtypo.worker.enabled` | `true` | Process scans in this instance |
| `jtypo.worker.max-concurrent-scans` | 4 | Scans processed at the same time by a worker |
| `jtypo.worker.stale-after-ms` | 120000 | A scan without heartbeat for this long is taken over by another worker |
| `jtypo.sse.max-duration-ms` | 1800000 | Maximum duration of a live stream; the browser reconnects |
| `jtypo.retention.scan-days` / `check-days` / `cron` | 90 / 30 / `0 30 3 * * *` | Purge of old data (§ 5bis) |

The API documentation describes the security and quota settings in more detail: [API.md](API.md).

### 5.2 Spring profiles

| Profile | Effect |
|---------|--------|
| *(none)* | API and worker in the same process (development, desktop) |
| `api` | API only; monitoring on port 8082 |
| `worker` | Worker only; its HTTP server on port 8082 only serves monitoring |

### 5.3 Desktop client

The Swing client reads `client.properties` (`api.url=http://localhost:8081`) and `config.properties`
(window size, preview, log paths). A `client.properties` placed next to the JAR overrides the embedded one.

---

## 5bis. Web Service Deployment (API and workers)

The backend JAR (`./gradlew :backend:bootJar`) runs in two roles, chosen with a Spring profile:

| Role | Profile | Ports | Processes scans | Needs Chromium | Network |
|------|---------|-------|-----------------|----------------|---------|
| API | `api` | 8081 (API), 8082 (monitoring) | no | no | behind the reverse proxy, reaches MySQL, S3, OIDC provider |
| Worker | `worker` | 8082 (monitoring only, API disabled) | yes | yes | **isolated**: MySQL and S3 only inside, Internet egress for checks |

```bash
SPRING_PROFILES_ACTIVE=api    java -jar jtyposquatting-backend.jar   # one or more instances
SPRING_PROFILES_ACTIVE=worker java -jar jtyposquatting-backend.jar   # scale the number of workers with the load
```

Workers coordinate through the `scans` table:

- each scan is claimed by a single worker, which sends a heartbeat while it works;
- if a worker dies, another one resumes its scans after `jtypo.worker.stale-after-ms` (2 min), without
  checking the domains already done.

API instances share sessions through MySQL (Spring Session).

Main environment variables:

| Variable | Used by | Description |
|----------|---------|-------------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | both | `jdbc:mysql://host:3306/jtypo`; schema created by Flyway |
| `STORAGE_TYPE=s3`, `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_PATH_STYLE` | both | Screenshot storage (any S3-compatible service; the bucket must exist) |
| `OIDC_ISSUER_URI`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET` | API | OpenID Connect provider |
| `SESSION_COOKIE_SECURE=true`, `FORWARD_HEADERS_STRATEGY=framework` | API | Behind the HTTPS reverse proxy (set in the API image) |
| `JTYPO_OUTBOUND_USER_AGENT` | worker | User-Agent with a contact URL |
| `JTYPO_SCREENSHOT_CHROMIUM_SANDBOX=false` | worker | In the container, which is the sandbox |
| `jtypo.worker.max-concurrent-scans` | worker | Scans processed at the same time (default 4) |
| `jtypo.analysis.max-concurrent-checks` | worker | Outbound checks at the same time (default 64) |

Development services (MySQL, Keycloak) are described in `docker-compose.yml`.

### Containers

| Image | Dockerfile | Contents |
|-------|-----------|----------|
| API | `backend/Dockerfile`, target `api` | Temurin 21 JRE, unprivileged user |
| Worker | `backend/Dockerfile`, target `worker` | Playwright Java 1.63 image (Java 21 + Chromium), user `pwuser` (about 4 GB) |
| Frontend | `web/Dockerfile` | Next.js standalone server on Node 22, port 3000 |

`deploy/docker-compose.prod.yml` assembles:

- nginx (`deploy/nginx/jtyposquatting.conf`);
- the frontend;
- the API;
- the workers;
- MySQL.

The identity provider and the S3 storage are external. Copy `deploy/.env.example` to `deploy/.env`, then:

```bash
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
```

- nginx serves the frontend and the API on the same origin, disables buffering for the SSE streams and
  limits the request rate (API and login).
- Containers run without capabilities, with a read-only root filesystem (tmpfs for `/tmp`, and for
  `/home/pwuser` on the workers).
- Workers are on an internal network (MySQL) and an egress network (Internet) only.
  - Also block the private ranges of the egress network on the host firewall (`DOCKER-USER` chain): the
    application-level anti-SSRF policy is the first barrier, the network the second.
  - The Chromium sandbox is disabled in the container (no user namespaces): the container is the sandbox.
- Terminate TLS in front of nginx (or add a 443 server) and send `X-Forwarded-Proto: https`.
- Register `https://<site>/login/oauth2/code/oidc` as redirect URI with the identity provider. Enable PKCE
  (always sent by the backend) and, for sign-up, a captcha.

### Monitoring

API instances and workers expose on port **8082**, never routed by nginx:

- `/actuator/health`, with the `/liveness` and `/readiness` probes;
- `/actuator/prometheus`.

Logs are written as JSON (ECS format) on the standard output of the containers, ready for a log collector.

| Metric | Description |
|--------|-------------|
| `jtypo_scans_submitted_total` | Scans created |
| `jtypo_scans_finished_total{status}` | Scans completed, cancelled or failed |
| `jtypo_scan_duration_seconds{status}` | Processing time of a scan |
| `jtypo_scans_queue{status}` | Scans waiting (`PENDING`) or in progress (`RUNNING`), from the database (aggregate with `max`) |
| `jtypo_checks_total{source}` | Domain checks served by the cache or done on the network |
| `jtypo_check_duration_seconds` | Duration of a network check |
| `jtypo_screenshots_total{outcome}` | Screenshots taken, failed or blocked by the anti-SSRF policy |
| `jtypo_quota_rejections_total{type}` | Requests refused: rate limit, simultaneous scans, daily quota |
| `jtypo_retention_deleted_total{type}` | Rows and screenshots deleted by the nightly purge |
| `jtypo_sse_streams` | Open live streams |

`deploy/prometheus/` contains a scrape configuration and alert rules. The alerts cover:

- an instance that is down;
- scans waiting with no worker processing them;
- a queue backlog;
- the failure rates of scans, of the API and of screenshots;
- a rate-limit spike.

### Backups and data retention

- `deploy/backup/mysql-backup.sh [dir]`: consistent dump (InnoDB, no lock), utf8mb4, gzip, completion check,
  keeps the last `KEEP` (14) backups. Schedule it daily.
- `deploy/backup/mysql-restore-test.sh <file>`: restores a backup into a throw-away MySQL container and
  checks the schema. Run it regularly: an untested backup is not a backup.
- `deploy/backup/mysql-restore.sh <file>`: restores into the deployment (stop `api` and `worker` first).
- Screenshots are in the S3 storage: enable versioning or replication on the bucket.

Workers purge old data every night (`jtypo.retention.cron`, 03:30):

| Property | Default | Deleted |
|----------|---------|---------|
| `jtypo.retention.scan-days` | 90 | Finished scans and their results (running scans are never purged) |
| `jtypo.retention.check-days` | 30 | Cached domain checks |
| — | — | Screenshots no longer referenced by any result or cached check |

### Continuous integration and delivery

See [§ 7](#7-cicd-with-github-actions). Images are pushed to `ghcr.io/<owner>/jtyposquatting-{api,worker,web}`
with the branch, version and commit tags. Deploying then means pulling these tags on the target host
(`docker compose pull && docker compose up -d`); script it for your hosting.

---

## 6. Native Installers (desktop)

`jpackage` (shipped with the JDK, `JPACKAGE_HOME` to point to another one) bundles the all-in-one JAR with a
Java runtime. Each installer is built on its target system.

| Command | Output | Requires |
|---------|--------|----------|
| `./gradlew :frontend:packageApp` | `dist/JTypoSquatting/` (application image) | — |
| `./gradlew :frontend:packageAppMsi` | `dist/JTypoSquatting-2.0-alpha1.msi` | Windows, WiX Toolset 3.x |
| `./gradlew :frontend:packageAppExe` | `dist/JTypoSquatting-2.0-alpha1.exe` | Windows, WiX Toolset 3.x |

Installers do not contain Chromium. Install it once after installation
(`java -cp JTypoSquatting.jar com.microsoft.playwright.CLI install chromium`), otherwise scans run without
screenshots.

---

## 7. CI/CD with GitHub Actions

The `Build and Release` workflow (`.github/workflows/build-and-release.yml`) runs:

- on every push and pull request to `master` or `main`;
- on `v*` tags;
- on manual launch.

| Job | Steps |
|-----|-------|
| `test` | JDK 21, Chromium and its system libraries, `./gradlew test` (Testcontainers MySQL and S3Mock on the runner's Docker engine) |
| `web` | Node 22, `npm ci`, ESLint, `npm run typecheck` (`next typegen` + `tsc`), Chromium, Playwright end-to-end tests on a production build |
| `build` | After `test`: Gradle build and all-in-one JAR, uploaded as an artifact; GitHub release on `v*` tags |
| `images` | After `test` and `web`: builds the `api`, `worker` and `web` images; pushes them to GHCR outside pull requests |
| `analyze-jar-size` | Reports the size and largest entries of the JAR |

Every Gradle call uses the JDK installed by `setup-java` (`-Dorg.gradle.java.home="$JAVA_HOME"`).

Tests that contact real websites (`@Tag("network")`) do not run in CI. Run them locally with
`./gradlew test -Dinclude.network=true`.

**Creating a release:**

```bash
# version=… in gradle.properties, then:
git tag v2.0-alpha1
git push origin v2.0-alpha1
```

The release contains `JTypoSquatting-<version>.jar` and `JTypoSquatting.jar`.

**Running the CI checks locally:**

```bash
./gradlew test
cd web && npm run lint && npm run typecheck && npm run test:e2e
```

---

## 8. Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md): build, login, API errors, live results, scans, screenshots,
tests, containers.

---

## 9. Uninstallation

| What | How |
|------|-----|
| All-in-one JAR | Delete the JAR; screenshots are in `${java.io.tmpdir}/jtyposquatting` |
| Native installer (Windows) | *Settings › Apps*, uninstall JTypoSquatting |
| Native application image (Linux) | Delete `dist/JTypoSquatting/` |
| Chromium used for screenshots | Delete `~/.cache/ms-playwright` (shared with other Playwright tools) |
| Development services | `docker compose down -v` (deletes the MySQL volume) |
| Web service | `docker compose -f deploy/docker-compose.prod.yml down`; add `-v` to delete the database, then empty the S3 bucket |

---

## Appendix A: File Locations

| Path | Content |
|------|---------|
| `backend/src/main/resources/application*.properties` | Backend configuration and profiles |
| `backend/src/main/resources/db/migration/{mysql,h2}` | Flyway migrations |
| `deploy/docker-compose.prod.yml`, `deploy/.env.example` | Production deployment |
| `deploy/nginx/`, `deploy/prometheus/`, `deploy/backup/` | Reverse proxy, monitoring, backups |
| `deploy/keycloak/jtypo-realm.json` | Development realm |
| `docker-compose.yml` | Development services (Keycloak, MySQL) |
| `web/messages/{fr,en}.json` | Frontend texts, including the provisional legal texts |
| `${java.io.tmpdir}/jtyposquatting` | Screenshots (`filesystem` storage) |
| `~/.cache/ms-playwright` | Chromium used for screenshots |

## Appendix B: Port Usage

| Port | Service |
|------|---------|
| 3000 | Frontend (Next.js; `npm run dev` or container) |
| 3100 | Frontend served by the end-to-end tests |
| 8081 | Backend API (desktop: 127.0.0.1 only) |
| 8082 | Monitoring of the `api` and `worker` instances (`/actuator/*`) |
| 8180 | Keycloak (development) |
| 3306 | MySQL (development: 127.0.0.1 only) |
| 80 | nginx (production compose, `HTTP_PORT`) |

## Appendix C: Quick Reference

```bash
# Desktop
./gradlew :frontend:fatJar && java -jar frontend/build/libs/JTypoSquatting.jar

# Web service, development
docker compose up -d keycloak
FORWARD_HEADERS_STRATEGY=framework ./gradlew :backend:bootRun
cd web && npm run dev

# Web service, production
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build

# Tests
./gradlew test
cd web && npm run test:e2e

# Backups
deploy/backup/mysql-backup.sh /var/backups/jtyposquatting
deploy/backup/mysql-restore-test.sh /var/backups/jtyposquatting/<file>.sql.gz
```
