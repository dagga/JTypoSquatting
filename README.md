# JTypoSquatting

**Find the domains that imitate yours.** JTypoSquatting generates the typo variants of a domain name
(typos, look-alike characters, other extensions…), checks which ones are online, and shows what they host.
It is meant for security analysts, brand protection and legal teams.

![Results of a scan](https://github.com/hernic/JTypoSquatting/assets/4397039/874a5ff7-68d5-4d8b-9a60-a4dddde188f9)

Version **2.0**. It comes in two forms:

| | Web service | Desktop application |
|---|---|---|
| Users | Several, with accounts (OpenID Connect) | One, on their own machine |
| Interface | Browser (Next.js, French / English) | Swing window |
| Storage | MySQL + S3-compatible storage | In memory (H2) |
| Run with | Containers (`deploy/`) | `java -jar JTypoSquatting.jar` |

---

## How it works

1. **Generate**: 21 algorithms turn a domain into its plausible variants, about 1,800 for `example.com`.
   Up to **50,000 domains** are checked per scan (`jtypo.quota.max-domains-per-scan`).
2. **Check**: each candidate is contacted.
   - HTTP response and TLS certificate (a self-signed or expired certificate is reported as a signal).
   - Page title, description and language (12 languages detected).
   - Screenshot taken in an isolated headless Chromium.
3. **Analyse**: results arrive live. Filter them, open the details, and export them as CSV or JSON.

### Generation algorithms

Each algorithm reproduces a mistake that a user makes, or a trick that a typosquatter uses. The examples
below are real outputs of the generators. The number in brackets is how many variants each algorithm
produces for `example.com`.

#### Typing mistakes on characters

| Algorithm | What it generates | Example |
|-----------|-------------------|---------|
| `omission` (7) | One character is missing: a key was not pressed hard enough. | `example.com` → `exmple.com` |
| `repetition` (7) | One character is typed twice. | `example.com` → `exxample.com` |
| `changeOrder` (6) | Two neighbouring characters are swapped, as when typing fast. | `example.com` → `exmaple.com` |
| `addition` (36) | One letter or digit is added at the end of the name. | `example.com` → `examplea.com`, `example1.com` |
| `vowelSwap` (15) | A vowel is replaced by another, a common spelling confusion. | `example.com` → `exemple.com` |

#### Keyboard mistakes (QWERTY and AZERTY)

| Algorithm | What it generates | Example |
|-----------|-------------------|---------|
| `replacement` (38) | A character is replaced by a neighbouring key on a QWERTY or AZERTY keyboard. | `example.com` → `wxample.com`; `amazon.com` → `qmazon.com` (AZERTY) |
| `doubleReplacement` | A doubled letter is replaced by a doubled neighbouring key. | `google.com` → `gppgle.com` |

#### Dots and dashes

| Algorithm | What it generates | Example |
|-----------|-------------------|---------|
| `addDash` (6) | A dash is inserted inside the name. | `example.com` → `ex-ample.com` |
| `stripDash` | The dashes of the name are removed. | `my-example.com` → `myexample.com` |
| `missingDot` | A dot is forgotten. Only names that end with a real extension are kept. | `www.example.com` → `wwwexample.com`; `example.co.uk` → `exampleco.uk` |
| `subdomain` (6) | A dot is inserted inside the name. The registered domain then becomes the end of the name, so whoever owns `ample.com` controls `ex.ample.com`. | `example.com` → `ex.ample.com` |
| `changeDotDash` | A dot is typed as a dash. Only names that end with a real extension are kept. | `www.example.com` → `www-example.com`; `example.co.uk` → `example-co.uk` |

#### Look-alike characters

| Algorithm | What it generates | Example |
|-----------|-------------------|---------|
| `homoglyph` (62) | A character is replaced by one that looks the same: accented letter, digit, or a pair of letters (`rn` for `m`). | `example.com` → `éxample.com`, `examp1e.com`, `exarnple.com` |

#### Words

| Algorithm | What it generates | Example |
|-----------|-------------------|---------|
| `commonMisspelling` | A word of the name is replaced by a common misspelling of it (list of frequent English errors). | `absence.com` → `absense.com` |
| `homophones` | A word is replaced by a word that sounds the same. | `fourseasons.com` → `forseasons.com` |
| `singularPluralize` (1) | A trailing `s` is added or removed. | `example.com` → `examples.com` |
| `numeralSwap` | A digit is written as a word, or a word as a digit. | `shop4you.com` → `shopforyou.com`; `oneclick.com` → `1click.com` |

#### Extensions

| Algorithm | What it generates | Example |
|-----------|-------------------|---------|
| `wrongTld` (1,435) | The extension is replaced by another one (all known extensions). | `example.com` → `example.fr`, `example.shop` |
| `addTld` (150) | A country code is appended, when that gives a registrable extension. | `example.com` → `example.com.br` |
| `wrongSld` | The second level of a country extension is changed. | `example.co.uk` → `example.org.uk` |
| `addDynamicDns` (14) | The name is registered under a free dynamic DNS service, often used for phishing. | `example.com` → `example.duckdns.org` |

Details and implementation: [Technical reference](doc/TECHNICAL_REFERENCE.md#2-domain-generation-algorithms).

### 🚧 Algorithms still to develop

Reference tools such as [dnstwist](https://github.com/elceef/dnstwist) and
[URLCrazy](https://github.com/urbanadventurer/urlcrazy) cover techniques that JTypoSquatting does not
generate yet:

| Algorithm | Status | What it would generate | Example |
|-----------|--------|------------------------|---------|
| **Combosquatting** (dictionary) | ⏳ To do | The brand combined with words used in phishing (login, secure, support, account, pay…). These are the most common phishing domains today. | `example.com` → `example-login.com`, `secure-example.com` |
| **Insertion** | ⏳ To do | A neighbouring key typed inside the name. `addition` only appends a character at the end. | `example.com` → `exsample.com` |
| **Bitsquatting** | ⏳ To do | One bit flipped in a character, caused by memory or transmission errors. | `example.com` → `dxample.com`, `uxample.com` |
| **Homoglyphs from other alphabets** | ⏳ To do | Cyrillic or Greek letters identical to Latin ones, alone or for the whole name at once. `homoglyph` only uses Latin look-alikes, one character at a time. | `example.com` → `ехаmple.com` (Cyrillic е, х, а) |
| **Extension inside the name** | ⏳ To do | The extension glued to the name, then another extension. | `example.com` → `examplecom.net`, `example-com.com` |

To add one:
1. Write a generator in `backend/src/main/java/com/aleph/graymatter/jtyposquatting/generator/`.
2. Declare it in the `Algorithm` enum: `NewAlgorithmsTest` checks that every declared algorithm generates
   variants.

Dictionary-based algorithms produce many candidates; the 50,000-domain limit per scan leaves room for them.

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
[compliance](doc/COMPLIANCE.md).

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
| [Compliance](doc/COMPLIANCE.md) | GDPR register, retention, users' rights, abuse handling (draft) |
| [Action plan](doc/ACTION_PLAN.md) | Roadmap towards the public web service and its progress |
| [Audit](doc/AUDIT.md) | Code audit behind the action plan |

---

## License

Public domain ([The Unlicense](LICENSE)).
