# JTypoSquatting

**Find the domains that imitate yours.** JTypoSquatting generates the typo variants of a domain name
(typos, look-alike characters, other extensions…), checks which ones are online, and shows what they host.
It is meant for security analysts, brand protection and legal teams.

![Results of a scan](https://github.com/hernic/JTypoSquatting/assets/4397039/874a5ff7-68d5-4d8b-9a60-a4dddde188f9)

Version **2.0-alpha1**. It comes in two forms:

| | Web service | Desktop application |
|---|---|---|
| Users | Several, with accounts (OpenID Connect) | One, on their own machine |
| Interface | Browser (Next.js, French / English) | Swing window |
| Storage | MySQL + S3-compatible storage | In memory (H2) |
| Run with | Containers (`deploy/`) | `java -jar JTypoSquatting.jar` |

---

## How it works

1. **Generate**: 21 algorithms turn `example.com` into thousands of candidates.
2. **Check**: each candidate is contacted.
   - HTTP response and TLS certificate (a self-signed or expired certificate is reported as a signal).
   - Page title, description and language (12 languages detected).
   - Screenshot taken in an isolated headless Chromium.
3. **Analyse**: results arrive live. Filter them, open the details, and export them as CSV or JSON.

| Family | Algorithms | Example |
|--------|-----------|---------|
| Characters | omission, repetition, changeOrder, addition, vowelSwap | `example.com` → `exmple.com`, `exmaple.com` |
| Keyboard | replacement, doubleReplacement (QWERTY and AZERTY) | `example.com` → `wxample.com`, `google.com` → `gppgle.com` |
| Dots and dashes | addDash, stripDash, missingDot, subdomain, changeDotDash | `example.com` → `ex-ample.com`, `ex.ample.com` |
| Look-alike characters | homoglyph | `example.com` → `ехаmple.com` (Cyrillic) |
| Words | commonMisspelling, homophones, singularPluralize, numeralSwap | `fourseasons.com` → `forseasons.com`, `shop4you.com` → `shopforyou.com` |
| Extensions | wrongTld, addTld, wrongSld, addDynamicDns | `example.com` → `example.fr`, `example.com.br`, `example.duckdns.org` |

Details: [Technical reference](doc/TECHNICAL_REFERENCE.md#25-algorithm-registry).

### 🚧 Algorithms still to develop

The 21 algorithms above are all implemented. Reference tools such as
[dnstwist](https://github.com/elceef/dnstwist) and [URLCrazy](https://github.com/urbanadventurer/urlcrazy)
cover other techniques that JTypoSquatting does not generate yet:

| Algorithm | Status | What it generates | Example |
|-----------|--------|-------------------|---------|
| **Combosquatting** (dictionary) | ⏳ To do | The brand combined with words used in phishing (login, secure, support, account, pay…). These are the most common phishing domains today. | `example.com` → `example-login.com`, `secure-example.com` |
| **Insertion** | ⏳ To do | A neighbouring key typed inside the label. `addition` only appends a character at the end. | `example.com` → `exsample.com` |
| **Bitsquatting** | ⏳ To do | One bit flipped in a character (memory or transmission errors). | `example.com` → `dxample.com`, `uxample.com` |
| **Whole-label homoglyphs** | ⏳ To do | All look-alike letters replaced at once, for example the whole label in Cyrillic. `homoglyph` replaces one character at a time. | `example.com` → `ехаmрlе.com` |
| **Extension inside the label** | ⏳ To do | The extension glued to the label, then another extension. | `example.com` → `examplecom.net`, `example-com.com` |

To add one, write a generator in `backend/src/main/java/com/aleph/graymatter/jtyposquatting/generator/`,
then declare it in the `Algorithm` enum: `NewAlgorithmsTest` checks that every declared algorithm generates
variants. Keep the cap on generated domains in mind (`jtypo.quota.max-domains-per-scan`, 3000 by default):
dictionary-based algorithms produce many candidates.

---

## Quick start

**Requirements**:
- Java 21+.
- For the web service: Node.js 22, plus Docker or Podman for Keycloak and MySQL.

### Web service, for local development

```bash
# 1. Identity provider (Keycloak on :8180, user alice / alice)
docker compose up -d keycloak

# 2. Headless Chromium for screenshots (once)
./gradlew :backend:installPlaywrightBrowsers

# 3. Backend: API and worker in one process on :8081, in-memory database
FORWARD_HEADERS_STRATEGY=framework ./gradlew :backend:bootRun

# 4. Frontend on http://localhost:3000 (/api and login are forwarded to the backend)
cd web && npm install && npm run dev
```

Log in with `alice` / `alice`, then start a scan from **My scans**. To use MySQL instead of the in-memory
database, run `docker compose up -d mysql` and start the backend with
`DB_URL=jdbc:mysql://localhost:3306/jtypo DB_USER=jtypo DB_PASSWORD=jtypo-dev`.

### Desktop application

```bash
./gradlew :frontend:fatJar :backend:installPlaywrightBrowsers
java -jar frontend/build/libs/JTypoSquatting.jar
```

The JAR starts the backend in *local* mode and opens the Swing window. In local mode the API listens on
127.0.0.1 only and needs no login. Guide: [Quick start](doc/QUICK_START.md).

---

## Architecture of the web service

```
Browser ──► nginx ─┬─ /          ─► Next.js (public pages + application)
                   └─ /api, login ─► API (Spring Boot) ──┬─► MySQL (scans, results, sessions)
                                                          ├─► S3 storage (screenshots)
                                                          └─► OpenID Connect provider
                     Workers (Spring Boot + Chromium) ◄── scan queue in MySQL
                     isolated network, Internet egress only
```

- **API instances** serve the REST API and the live results (Server-Sent Events). They hold no state:
  sessions are kept in MySQL.
- **Workers** claim queued scans and send a heartbeat while they work. If a worker dies, another one
  resumes its scans without checking the same domains again.
- A cache shared between users avoids checking the same domain twice within 24 hours.

### Security

| Area | Measures |
|---|---|
| Login | OpenID Connect with PKCE, session in an httpOnly cookie, CSRF token, strict security headers |
| Isolation between users | Each user only sees their own scans, results and screenshots |
| Scanned sites | Never reach internal addresses (DNS pinning, redirects checked again), strict TLS validation |
| Screenshots | Hostile pages open in a fresh Chromium context, inside unprivileged containers |
| Abuse | Rate limiting, per-user quotas, cap on outbound checks |

### Operations

- Prometheus metrics, JSON logs and alert rules.
- Nightly purge: scans after 90 days, cached checks after 30 days, screenshots that are no longer used.
- Backup and restore scripts for MySQL.
- GDPR: users can export their data and delete their account.

---

## Project structure

```
JTypoSquatting/
├── backend/     Spring Boot: REST API, scan workers, generators, analysis engine, Dockerfile
├── shared/      DTOs and domain name model shared with the desktop client
├── frontend/    Swing desktop client (and the all-in-one JAR)
├── web/         Next.js frontend of the web service, Playwright end-to-end tests, Dockerfile
├── deploy/      Production compose, nginx, Prometheus, Keycloak realm, backup scripts
└── doc/         Documentation
```

---

## Tests

```bash
./gradlew test                          # Backend: unit, Spring and Testcontainers tests (MySQL, S3)
./gradlew test -Dinclude.network=true   # Also the tests that contact real websites

cd web
npm run lint && npm run typecheck
npm run test:e2e                        # Playwright on a production build, simulated backend
```

The Testcontainers tests need Docker or Podman and are skipped otherwise. With rootless Podman, set
`DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock` and `TESTCONTAINERS_RYUK_DISABLED=true`.

GitHub Actions runs:
- the backend tests;
- the frontend checks and end-to-end tests;
- the builds of the `api`, `worker` and `web` images, which are published to GHCR outside pull requests.

---

## Deployment

```bash
cp deploy/.env.example deploy/.env    # database, S3, OpenID Connect, public URL
docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env up -d --build
```

Before going live, also take care of the following:
- TLS in front of nginx;
- firewall rules for the workers' network;
- scheduled backups;
- the legal texts.

See the [deployment guide](doc/DEPLOYMENT.md#5bis-web-service-deployment-api-and-workers) and
[compliance](doc/CONFORMITE.md).

---

## Documentation

| Document | Content |
|----------|---------|
| [Quick start](doc/QUICK_START.md) | Desktop application in a few minutes |
| [Deployment](doc/DEPLOYMENT.md) | Building, configuration, containers, monitoring, backups |
| [API](doc/API.md) | Authentication, scans, live results, quotas, screenshots |
| [Technical reference](doc/TECHNICAL_REFERENCE.md) | Algorithms, mappings, classes |
| [Architecture](doc/ARCHITECTURE.md) | Software architecture |
| [Troubleshooting](doc/TROUBLESHOOTING.md) | Common issues |
| [Compliance](doc/CONFORMITE.md) | GDPR register, retention, users' rights, abuse handling (draft, in French) |
| [Action plan](doc/plan-action.md) | Roadmap towards the public web service and its progress (in French) |
| [Audit](doc/audit.md) | Code audit behind the action plan (in French) |

---

## License

Public domain ([The Unlicense](LICENSE)).
