# JTypoSquatting - Technical Reference

**Version:** 2.0-alpha1
**Last Updated:** October 2026 (branch `feature/web-public`)

---

## Table of Contents

1. [Code Organization](#1-code-organization)
2. [Domain Generation Algorithms](#2-domain-generation-algorithms)
3. [Domain Checks](#3-domain-checks)
4. [Class Reference](#4-class-reference)
5. [Database Schema](#5-database-schema)
6. [Configuration Reference](#6-configuration-reference)
7. [Development Guidelines](#7-development-guidelines)

---

## 1. Code Organization

```
JTypoSquatting/
├── shared/    DTOs and domain name model (com.aleph.graymatter.jtyposquatting.{dto,net})
├── backend/   Spring Boot application (com.aleph.graymatter.jtyposquatting.*)
│   └── src/main/resources/
│       ├── application*.properties   Configuration (default, api, worker profiles)
│       ├── db/migration/{mysql,h2}/  Flyway migrations, one set per database vendor
│       ├── TLD.txt                   1436 top-level domains (wrongTld, addTld)
│       ├── common-misspellings.json  4291 misspelled → correct word pairs (commonMisspelling)
│       └── dynamic-dns.json          Dynamic DNS warning list (consistency check of addDynamicDns)
├── frontend/  Swing desktop client and the all-in-one JAR (CombinedLauncher)
├── web/       Next.js frontend of the web service
└── deploy/    Production compose, nginx, Prometheus, Keycloak realm, backup scripts
```

The same backend artifact runs as an **API** instance, a **worker** instance (Spring profiles `api`,
`worker`) or as the backend of the desktop application (`jtypo.security.mode=local`).

---

## 2. Domain Generation Algorithms

### 2.1 Registry and pipeline

Every algorithm is declared in the `generator.Algorithm` enum, bound to its implementation. For a scan,
`JTypoSquatting` applies all 21 algorithms to the scanned domain:
1. It merges the results and removes duplicates and the scanned domain itself.
2. The worker converts each candidate to its ASCII/punycode form (`DomainKeys`).
3. The worker keeps at most `jtypo.quota.max-domains-per-scan` candidates (**50 000** by default).

**Volume:** a domain currently produces about **1 800 candidates**: 1780 for `example.com`, 1891 for
`examplebrand.com`, 1657 for `www.mybank.co.uk`. `wrongTld` alone accounts for about 1435 of them.

**Shared helpers:**
- `DomainParts` splits a name with the Public Suffix List into subdomain, registrable label and suffix,
  so `www.example.co.uk` gives `www` / `example` / `co.uk`. The newer generators (`CharacterEdits`,
  `Keyboard`, `DotDash`, `Words`, `Suffixes`) only change the label and keep the subdomain and the suffix.
- The original generators (`Dash`, `Homoglyph`, `Misspell`, `TLD`) work on everything before the last
  label.
- Every candidate goes through `DomainName` validation (Guava `InternetDomainName`), and invalid names are
  dropped.

The examples below come from the generators themselves (run on the current code).

### 2.2 The 21 algorithms

#### Characters — `CharacterEdits`

| Id | What it generates | Why it is a realistic mistake | Example |
|----|-------------------|-------------------------------|---------|
| `omission` | Each character of the label removed in turn | A key not pressed hard enough | `example.com` → `exmple.com`, `xample.com` |
| `repetition` | Each character typed twice (dashes excluded) | Key bounce, double press | `example.com` → `exxample.com`, `exammple.com` |
| `changeOrder` | Two adjacent different characters swapped | Fingers in the wrong order | `example.com` → `exmaple.com`, `exampel.com` |
| `addition` | One character among a–z and 0–9 appended at the end of the label | Extra key at the end of typing | `example.com` → `examplea.com`, `example1.com` |
| `vowelSwap` | Each vowel (`aeiouy`) replaced by every other vowel | Phonetic confusion | `example.com` → `exemple.com`, `axample.com` |

#### Keyboard — `Keyboard`

Key adjacency is computed from the rows of the **QWERTY and AZERTY** layouts, digit row included, with
staggered rows.

| Id | What it generates | Why it is a realistic mistake | Example |
|----|-------------------|-------------------------------|---------|
| `replacement` | Each character replaced by a neighbouring key | Fat finger | `example.com` → `wxample.com`, `3xample.com`, `ezample.com` |
| `doubleReplacement` | A doubled letter replaced by a doubled neighbouring key | The finger is misplaced on both strokes | `google.com` → `gppgle.com`, `g00gle.com` |

#### Dots and dashes — `Dash`, `DotDash`

| Id | Class | What it generates | Why it is a realistic mistake | Example |
|----|-------|-------------------|-------------------------------|---------|
| `addDash` | `Dash` | A dash inserted between each pair of characters of the label | Branded names are often split | `example.com` → `ex-ample.com`, `exam-ple.com` |
| `stripDash` | `Dash` | All dashes removed from the label | Users forget the dash | `my-example.com` → `myexample.com` |
| `missingDot` | `DotDash` | One dot removed. Only names that still end with a public suffix are kept: `www.examplecom` is not generated. | The dot after `www` is forgotten | `www.example.com` → `wwwexample.com`; `example.co.uk` → `exampleco.uk` |
| `subdomain` | `DotDash` | A dot inserted inside the label, not next to a dash | Looks like a subdomain of a shorter domain | `example.com` → `ex.ample.com`, `exam.ple.com` |
| `changeDotDash` | `DotDash` | One dot typed as a dash. Same public-suffix filter as `missingDot`. | `-` and `.` are confused | `www.example.com` → `www-example.com`; `example.co.uk` → `example-co.uk` |

#### Look-alike characters — `Homoglyph`

| Id | What it generates | Why it is a realistic mistake | Example |
|----|-------------------|-------------------------------|---------|
| `homoglyph` | Each character replaced, **one at a time**, by look-alikes from `Const.SIMILAR_CHAR`: accented Latin letters, digits for letters and the reverse, letter pairs (`rn` for `m`, `vv` for `w`, `cl` for `d`) | Visually indistinguishable in an address bar or an e-mail | `example.com` → `éxample.com`, `exämple.com`, `examp1e.com`, `exarnple.com` (62 variants) |

`SIMILAR_CHAR` holds **Latin look-alikes only**: no Cyrillic or Greek letters are generated (see § 2.3).

#### Words — `Misspell`, `Words`

| Id | Class | What it generates | Why it is a realistic mistake | Example |
|----|-------|-------------------|-------------------------------|---------|
| `commonMisspelling` | `Misspell` | Each correct word found in the name is replaced by its common misspellings (4291 pairs in `common-misspellings.json`; one word can have several misspellings) | Real spelling mistakes | `absence.com` → `absense.com`, `abscence.com` |
| `homophones` | `Words` | A word of a homophone group (`Const.HOMOPHONES`, 41 groups such as for/four, to/too/two, hour/our) replaced by the other words of its group | Words that sound alike | `fourseasons.com` → `forseasons.com` |
| `singularPluralize` | `Words` | A trailing `s` added or removed (`es` for labels ending in x, z, ch, sh) | Singular and plural are confused | `example.com` → `examples.com`; `examples.com` → `example.com` |
| `numeralSwap` | `Words` | A digit written as a word (`Const.NUMERAL`) and the reverse | Spoken URLs are transcribed | `shop4you.com` → `shopforyou.com`, `shopfouryou.com`; `oneclick.com` → `1click.com` |

#### Extensions — `TLD`, `Suffixes`

| Id | Class | What it generates | Why it is a realistic mistake | Example |
|----|-------|-------------------|-------------------------------|---------|
| `wrongTld` | `TLD` | The extension replaced by each of the 1436 TLDs of `TLD.txt` | Wrong extension, or cheap registration under another TLD | `example.com` → `example.fr`, `example.de`, `example.co` (1435 variants) |
| `addTld` | `Suffixes` | A two-letter country code appended, only when the result is a registrable public suffix | `example.com.br`-style suffixes exist in many countries | `example.com` → `example.com.br`, `example.com.au` (150 variants) |
| `wrongSld` | `Suffixes` | The second level of a country suffix replaced by a common one (`Const.COMMON_SLD`: co, com, net, org, ac, gov…), only if the result is a public suffix | `co.uk` and `org.uk` are confused | `example.co.uk` → `example.org.uk`, `example.ac.uk` |
| `addDynamicDns` | `Suffixes` | The label registered under 14 free dynamic DNS providers (`Const.DYNAMIC_DNS_PROVIDERS`, all present in `dynamic-dns.json`) | Free and anonymous hosting, often used by phishing kits | `example.com` → `example.duckdns.org`, `example.ddns.net` |

### 2.3 Algorithms still to develop

Reference tools such as dnstwist and URLCrazy cover techniques that are not implemented yet.

| Algorithm | What it would generate | Why it matters | Example |
|-----------|------------------------|----------------|---------|
| **Combosquatting** (dictionary) | The brand combined with words used in phishing (login, secure, support, account, pay…), with or without a dash, before or after | This is the most common pattern of current phishing domains | `example.com` → `example-login.com`, `secure-example.com` |
| **Insertion** | A neighbouring key inserted before or after each character (`addition` only appends at the end) | A finger brushes the next key | `example.com` → `exsample.com` |
| **Bitsquatting** | Each character with one bit flipped, when the result is a valid DNS character | Memory and transmission errors in devices | `example.com` → `dxample.com`, `uxample.com` |
| **Homoglyphs from other alphabets / whole label** | Cyrillic or Greek look-alikes, and every look-alike letter replaced at once instead of one at a time | Internationalised names identical to the original | `example.com` → `ехаmрlе.com` (Cyrillic е, х, а, р, е) |
| **Extension inside the label** | The extension glued to the label, then another extension | Survives a quick reading of the address | `example.com` → `examplecom.net`, `example-com.com` |

**To add an algorithm:**
1. Write a static generator `(DomainName, List<DomainName>)` in the `generator` package. Use `DomainParts`
   to keep the subdomain and the suffix.
2. Declare it in `Algorithm`.
3. Give it a sample domain in `NewAlgorithmsTest`, which checks that every declared algorithm produces
   variants.

Dictionary-based algorithms multiply the volume: the 50 000 cap per scan is sized for them.

---

## 3. Domain Checks

**Checking a candidate** (`scan.DomainChecker` → `service.DomainCheckService` → `service.PageAnalyzer`):
1. **Shared cache.** If the domain was checked less than `jtypo.cache.ttl-hours` ago (24 h), by any user,
   the cached result is reused (table `domain_checks`), unless the scan asks for `forceRefresh`.
2. **Instance-wide cap.** At most `jtypo.analysis.max-concurrent-checks` (64) checks run at the same time
   on an instance.
3. **HTTP fetch** through Apache HttpClient 5:
   - The DNS resolver (`GuardedDnsResolver`) rejects private, loopback, link-local, metadata, CGNAT,
     reserved, multicast, NAT64, 6to4, Teredo and ULA addresses. The connection goes to the address that
     was checked.
   - Only http and https, and only ports 80/443, are allowed. They are checked again on each redirect
     (5 at most).
   - The body is read up to 2 MB.
4. **TLS** is validated strictly. An invalid certificate is recorded as `TlsStatus` (`EXPIRED`,
   `HOSTNAME_MISMATCH`, `UNTRUSTED`, `HANDSHAKE_FAILED`); the page is then fetched over plain HTTP.
5. **Page content.** JSoup extracts the title, the meta description, keywords, author and Open Graph
   tags, and the first 200 characters of text. Lingua detects the language among 12 languages: English,
   French, German, Spanish, Italian, Portuguese, Dutch, Russian, Chinese, Japanese, Korean and Arabic.
6. **Screenshot**, taken only for 2xx responses whose text has at least 5 characters:
   - `PlaywrightScreenshotService` runs headless Chromium, with a fresh context per capture, downloads and
     service workers disabled.
   - Every request of the page goes through the same anti-SSRF policy.
   - The PNG is stored by content hash in the `ScreenshotStore`: S3 or the file system.

**Resulting status:**

| HTTP code | Status |
|-----------|--------|
| 200 | `SUSPICIOUS` |
| any other code > 0 (redirects, 403…) | `SAFE` |
| 0 (DNS failure, connection refused, timeout, blocked destination) | `UNREACHABLE` |

---

## 4. Class Reference

Backend packages under `com.aleph.graymatter.jtyposquatting`:

| Package | Classes | Role |
|---------|---------|------|
| (root) | `JTypoSquattingApplication`, `JTypoSquatting` | Spring Boot entry point; applies the 21 algorithms |
| `generator` | `Algorithm`, `DomainParts`, `CharacterEdits`, `Keyboard`, `Dash`, `DotDash`, `Homoglyph`, `Misspell`, `Words`, `Suffixes`, `TLD` | Generation (§ 2) |
| `constants` | `Const` | Look-alike characters, numerals, homophones, dynamic DNS providers, common SLDs |
| `scan` | `ScanService`, `ScanController`, `ScanWorker`, `ScanEventStreamer`, `DomainChecker`, `ScanRepository`, `ScanResultRepository`, `DomainCheckRepository`, `UserRepository`, `ScanQueueMetrics`, records `Scan`, `ScanResult`, `DomainCheck`, `ScanUser`, `ScanDTO`, `ScanResultDTO`, `ScanStatus`, exceptions | Web scans: creation and persistent quotas, worker queue (atomic claim, heartbeat, takeover), SSE, shared cache |
| `security` | `OutboundPolicy`, `GuardedDnsResolver`, `BlockedDestinationException` | Anti-SSRF policy of every outbound connection, User-Agent |
| `service` | `PageAnalyzer`, `DomainCheckService`, `ScreenshotService`, `PlaywrightScreenshotService` | HTTP/TLS analysis of a page, screenshots |
| `storage` | `ScreenshotStore`, `S3ScreenshotStore`, `FileSystemScreenshotStore`, `StorageConfig`, `ScreenshotController`, `ScreenshotGarbageCollector` | Content-addressed screenshot storage, access-controlled download, deletion of unreferenced screenshots |
| `abuse` | `RateLimitFilter`, `QuotaExceededException`, `QuotaExceptionHandler` | Request rate limit (Bucket4j), 429 responses with `Retry-After` |
| `account` | `AccountService`, `AccountController` | GDPR: data export, account deletion |
| `retention` | `RetentionService` | Nightly purge (workers only) |
| `persistence` | `DomainKeys` | Stored form of a domain (lower-case punycode) ↔ Unicode form |
| `config` | `SecurityConfig`, `LazyClientRegistrationRepository`, `JsonLogoutSuccessHandler`, `SchedulingConfig` | OIDC login with PKCE, CSRF, headers, `local` mode; lazy discovery; JSON logout; scheduling on workers |
| `controller` | `UserController` (`GET /api/me`), `TypoSquattingController`, `DomainDataController` | The last two are the legacy API of the desktop application, loaded in `local` mode only |
| `db` | `DatabaseService` | Legacy table `domain_page_data` of the desktop application |

`shared` module:
- `dto.DomainResultDTO`, `DomainPageDTO`, `DomainStatus`, `TlsStatus`, `GenerationRequestDTO` (body of
  `POST /api/scans`);
- `net.DomainName`;
- `InvalidDomainException`.

---

## 5. Database Schema

Flyway applies `classpath:db/migration/{vendor}`: one set for **MySQL 8.4** (web service), one for **H2**
(desktop application and fast tests). Both sets are kept in sync.

| Migration | Content |
|-----------|---------|
| V1 `web_schema` | `users`, `scans`, `domain_checks`, `scan_results` |
| V2 `spring_session` | `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` (copied from spring-session-jdbc 3.5.0) |
| V3 `legacy_domain_page_data` | `domain_page_data` (desktop application, to drop with the Swing client) |
| V4 `screenshot_key_index` | Index on `scan_results.screenshot_key` (screenshot access control) |

| Table | Key | Main columns |
|-------|-----|--------------|
| `users` | `id` (OIDC subject) | `username`, `email`, `created_at`, `last_seen_at` |
| `scans` | `id` (UUID) | `user_id` → users (cascade), `base_domain` (punycode), `base_domain_unicode`, `status` (PENDING, RUNNING, COMPLETED, CANCELLED, FAILED), `force_refresh`, `total_domains`, `checked_domains`, `worker_id`, `heartbeat_at`, `created_at`, `started_at`, `finished_at`, `error`. The table is also the job queue. |
| `domain_checks` | `domain` (punycode) | Shared cache: `status`, `http_code`, `tls_status`, `title`, `language`, `description`, `homepage_text`, `http_headers` (JSON), `screenshot_key`, `checked_at` |
| `scan_results` | `id` (auto-increment, used as SSE event id) | `scan_id` → scans (cascade), unique (`scan_id`, `domain`), same check columns as `domain_checks`, stored as a snapshot |

On MySQL, domain columns use `CHARACTER SET ascii COLLATE ascii_bin`. The default `utf8mb4_0900_ai_ci`
collation ignores accents, so `exämple.com` would equal `example.com`. Text columns use
`utf8mb4_0900_bin`. Screenshots are never stored in the database, only their key.

---

## 6. Configuration Reference

Values below are the defaults of `application.properties` and of the `@Value` annotations. Environment
variables in parentheses override them.

| Property | Default | Description |
|----------|---------|-------------|
| `jtypo.security.mode` | `oidc` | `oidc` (web service) or `local` (desktop application, set by `CombinedLauncher`) |
| `jtypo.oidc.issuer-uri` (`OIDC_ISSUER_URI`) | `http://localhost:8180/realms/jtypo` | Issuer, discovered at the first login |
| `jtypo.oidc.client-id` / `client-secret` (`OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET`) | `jtypo-web` / `dev-secret-change-me` | Confidential client |
| `jtypo.security.post-login-url` | `/app` | Page after login |
| `jtypo.security.post-logout-url` | `{baseUrl}/` | Page after logout at the identity provider |
| `jtypo.security.login-failure-url` | `/app?loginError=1` | Page after a failed login |
| `jtypo.api.enabled` | `true` | `false` on workers: API controllers disabled |
| `jtypo.worker.enabled` | `true` | `false` on API instances: no scan processing, no purge |
| `jtypo.worker.max-concurrent-scans` | `4` | Scans processed at the same time by a worker |
| `jtypo.worker.poll-interval-ms` | `1000` | Polling of the queue |
| `jtypo.worker.heartbeat-interval-ms` | `5000` | Heartbeat of a running scan |
| `jtypo.worker.stale-after-ms` | `120000` | A running scan without heartbeat is taken over |
| `jtypo.quota.max-concurrent-scans` | `2` | Active scans per user |
| `jtypo.quota.scans-per-day` | `50` | Scans per user over 24 sliding hours |
| `jtypo.quota.max-domains-per-scan` | `50000` | Generated domains checked per scan |
| `jtypo.rate-limit.requests-per-minute` | `120` | API requests per user (per IP when anonymous) |
| `jtypo.analysis.max-concurrent-checks` | `64` | Outbound checks at the same time on an instance |
| `jtypo.cache.ttl-hours` | `24` | Validity of a cached check |
| `jtypo.outbound.allow-private-addresses` | `false` | Never `true` in production (tests only) |
| `jtypo.outbound.allowed-ports` | `80,443` | Ports of outbound connections |
| `jtypo.outbound.user-agent` | `Mozilla/5.0 (compatible; JTypoSquatting/2.0)` | User-Agent of the HTTP client and the browser |
| `jtypo.screenshot.enabled` | `true` | Screenshots |
| `jtypo.screenshot.chromium-sandbox` | `true` | Disable only inside an isolated container |
| `jtypo.screenshot.timeout-ms` | `15000` | Navigation timeout |
| `jtypo.screenshot.max-concurrent` | `2` | Chromium instances per process |
| `jtypo.sse.poll-interval-ms` | `500` | Database polling of a live stream |
| `jtypo.sse.keep-alive-ms` | `15000` | Keep-alive comment without activity |
| `jtypo.sse.max-duration-ms` | `1800000` | Duration of a stream before the browser reconnects |
| `jtypo.storage.type` (`STORAGE_TYPE`) | `filesystem` | `filesystem` or `s3` |
| `jtypo.storage.directory` (`STORAGE_DIRECTORY`) | `${java.io.tmpdir}/jtyposquatting` | Directory of the `filesystem` store |
| `jtypo.storage.s3.endpoint` / `region` / `bucket` (`S3_ENDPOINT` / `S3_REGION` / `S3_BUCKET`) | — / `us-east-1` / `jtypo-screenshots` | S3-compatible storage |
| `jtypo.storage.s3.access-key` / `secret-key` / `path-style` (`S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_PATH_STYLE`) | — / — / `true` | Credentials and URL style |
| `jtypo.retention.scan-days` | `90` | Finished scans are purged after this many days |
| `jtypo.retention.check-days` | `30` | Cached checks are purged after this many days |
| `jtypo.retention.cron` | `0 30 3 * * *` | Purge schedule |
| `jtypo.retention.max-scans-per-run` | `5000` | Scans deleted per purge run |

**Other settings:**
- Database: `DB_URL` (default `jdbc:h2:mem:jtypo`), `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE` (20).
- Sessions: `spring.session.timeout=8h`; cookie `httpOnly`, `SameSite=Lax`, `Secure` with
  `SESSION_COOKIE_SECURE`.
- `FORWARD_HEADERS_STRATEGY`: `framework` behind the reverse proxy.
- Management: endpoints `health`, `info` and `prometheus`, on port 8082 in the `api` and `worker`
  profiles.

**Profiles:**

| Profile | Settings |
|---------|----------|
| `api` | `jtypo.worker.enabled=false`, `management.server.port=8082` |
| `worker` | `jtypo.worker.enabled=true`, `jtypo.api.enabled=false`, `server.port=8082` (monitoring only) |

---

## 7. Development Guidelines

- **Run the tests:**
  - Unit, Spring and Testcontainers tests: `./gradlew test`. MySQL and S3Mock run when a container
    engine is available.
  - Tests that contact real websites: `-Dinclude.network=true`.
- **New algorithm:** see § 2.3.
- **Schema change:** add a migration to **both** `db/migration/mysql` and `db/migration/h2`, with the same
  version number. Store domains with `DomainKeys.toKey`.
- **New outbound connection:** it must go through `OutboundPolicy` (resolution and URL check). Never
  connect to a user-controlled host directly.
- **Beans with two constructors** (one for Spring, one for tests): annotate the Spring one with
  `@Autowired`.
