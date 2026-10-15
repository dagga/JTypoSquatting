# JTypoSquatting — API Documentation

**Version:** 2.0-alpha1
**Last updated:** October 2026
**Base URL:** `http://localhost:8081/api` (development and desktop). In production, the reverse proxy serves
the API on the same origin as the frontend: `https://<site>/api`.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication and security](#2-authentication-and-security)
3. [Scans (web service)](#3-scans-web-service)
4. [Desktop application endpoints (local mode only)](#4-desktop-application-endpoints-local-mode-only)
5. [Data formats](#5-data-formats)
6. [Errors, rate limiting and quotas](#6-errors-rate-limiting-and-quotas)
7. [Server-Sent Events](#7-server-sent-events)
8. [Examples](#8-examples)
- [Appendix A: Status values](#appendix-a-status-values)
- [Appendix B: Monitoring endpoints](#appendix-b-monitoring-endpoints)
- [Appendix C: Changelog](#appendix-c-changelog)

---

## 1. Overview

- REST over HTTP, JSON bodies (`application/json`), live results as Server-Sent Events
  (`text/event-stream`).
- Dates are ISO-8601 instants in UTC (`2026-10-14T17:02:11.123Z`).
- Domains are accepted in Unicode (`éxample.com`). They are stored in ASCII/punycode
  (`xn--xample-9ua.com`) and returned in both forms.

### 1.1 Endpoints summary

**Web service.** These endpoints exist in every mode. In web mode they require a login, see
[§ 2](#2-authentication-and-security).

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/me` | Current user |
| GET | `/api/me/export` | All data of the user (GDPR access and portability) |
| DELETE | `/api/me` | Delete the account and its data |
| POST | `/api/scans` | Queue a scan (202) |
| GET | `/api/scans` | Last 50 scans of the user |
| GET | `/api/scans/{id}` | Scan status and counters |
| GET | `/api/scans/{id}/results?after=&limit=` | Results, page by page |
| GET | `/api/scans/{id}/events` | Live results (SSE, resumable with `Last-Event-ID`) |
| DELETE | `/api/scans/{id}` | Cancel a scan |
| GET | `/api/screenshots/{sha256}` | Screenshot (PNG) |
| GET | `/oauth2/authorization/oidc` | Start the OpenID Connect login (browser navigation) |
| POST | `/logout` | End the session; returns the identity provider logout URL |

**Desktop application only.** These endpoints exist in local mode only (`jtypo.security.mode=local`). They
act on the data and analyses of every user, so they are not loaded in web mode, where they return **404**.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/generate-and-check?domain=` | Generate and check the variants of a domain (SSE) |
| GET | `/api/cancel` | Cancel every running analysis |
| DELETE | `/api/cancel-and-clear` | Cancel every analysis and clear the stored pages |
| GET | `/api/data/analyze?domain=` | Analyse one domain |
| GET | `/api/data/analyze-and-save?domain=` | Analyse one domain and store the page (SSE) |
| GET | `/api/data/cached/{domain}` | Stored page of a domain |
| GET | `/api/data/all` | All stored pages |
| DELETE | `/api/data/all` | Delete all stored pages |
| GET | `/api/data/stats` | Number of stored pages |

---

## 2. Authentication and security

The security mode is selected with `jtypo.security.mode`.

### 2.1 Web mode (`oidc`, default)

**Login.**

- Users log in through an OpenID Connect provider (Keycloak in development, see `docker-compose.yml`).
- Browsers start at `/oauth2/authorization/oidc`. The backend redirects to the provider with PKCE
  (`code_challenge_method=S256`) and receives the code on `/login/oauth2/code/oidc`.
- After a successful login, the browser goes to `jtypo.security.post-login-url` (`/app`). After a failed
  login, it goes to `/app?loginError=1`.
- The provider configuration is discovered at the first login, so the API starts even when the provider
  is down.

**Session.**

- The session is kept in an httpOnly cookie named `SESSION` (Spring Session, stored in the database and
  shared by the API instances).
- The same cookie authenticates the SSE streams: `EventSource` cannot send an `Authorization` header.

**Unauthenticated calls.** Every `/api/**` call without a valid session returns **401 Unauthorized**. The
API never redirects to the login page.

**CSRF.**

- Requests that modify data (`POST`, `PUT`, `DELETE`, including `POST /logout`) require the CSRF token.
- Read the `XSRF-TOKEN` cookie, which any API response sets, and send its value back in the
  `X-XSRF-TOKEN` header. Otherwise the response is **403 Forbidden**.

**Logout.** `POST /logout` ends the session and returns **200** with
`{"logoutUrl": "<identity provider end-session URL>"}`. The frontend then navigates to that URL itself,
because a `fetch()` cannot follow a redirect to another origin.

| Property | Environment variable | Description |
|----------|---------------------|-------------|
| `jtypo.oidc.issuer-uri` | `OIDC_ISSUER_URI` | Issuer URL, discovered at the first login |
| `jtypo.oidc.client-id` | `OIDC_CLIENT_ID` | Confidential client id |
| `jtypo.oidc.client-secret` | `OIDC_CLIENT_SECRET` | Client secret |
| `jtypo.security.post-login-url` | — | Page after login (default `/app`) |
| `jtypo.security.login-failure-url` | — | Page after a failed login (default `/app?loginError=1`) |
| `server.servlet.session.cookie.secure` | `SESSION_COOKIE_SECURE` | Must be `true` behind HTTPS |
| `server.forward-headers-strategy` | `FORWARD_HEADERS_STRATEGY` | `framework` behind the reverse proxy (HTTPS and public host detection) |

### 2.2 Security headers

Every response carries:

- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`;
- `Referrer-Policy: strict-origin-when-cross-origin`;
- `Permissions-Policy`;
- `X-Content-Type-Options: nosniff`;
- `X-Frame-Options: DENY`;
- over HTTPS, `Strict-Transport-Security`.

CORS is disabled: the frontend and the API are served from the same origin by the reverse proxy (by the
Next.js rewrites in development).

### 2.3 Desktop mode (`local`)

The all-in-one JAR (`CombinedLauncher`) sets this mode.

- The API listens on `127.0.0.1` only and requires no login, no CSRF token and no rate limit.
- `GET /api/me` returns **204 No Content**.
- The web-service endpoints are available too, with a single implicit user, `local`.

Never use this mode on a server.

### 2.4 Current user and account

#### GET /api/me

**200**:

```json
{ "id": "f3c1…", "username": "alice", "name": "Alice Dev", "email": "alice@example.com" }
```

#### GET /api/me/export

Returns **200** with `Content-Disposition: attachment; filename="jtyposquatting-data.json"`. The body holds
everything stored about the user:

```json
{
  "exportedAt": "2026-10-14T17:10:00Z",
  "user": { "id": "f3c1…", "username": "alice", "email": "alice@example.com",
            "createdAt": "…", "lastSeenAt": "…" },
  "scans": [ { "scan": { "id": "3f0c…", "baseDomain": "example.com", "status": "COMPLETED", "…": "…" },
               "results": [ { "id": 1234, "domain": "exmple.com", "…": "…" } ] } ]
}
```

#### DELETE /api/me

Requires the CSRF header. Returns **204**. The call deletes:

- the profile, the scans and the results;
- the screenshots that no other user needs;
- the other sessions of the user.

The current session is kept, so that the frontend can then call `POST /logout`, which also ends the
identity provider session. Otherwise the next login would be silent and recreate the account.

---

## 3. Scans (web service)

A scan generates the typo variants of a domain and checks them. It is queued and processed by a worker,
possibly on another instance. Its results are stored, so the browser can be closed and the scan followed
again later.

### 3.1 Queue a scan

```http
POST /api/scans
Content-Type: application/json
X-XSRF-TOKEN: <XSRF-TOKEN cookie value>

{ "baseDomain": "example.com", "forceRefresh": false }
```

**202 Accepted**, `Location: /api/scans/{id}`, body: the scan ([§ 5.1](#51-scan)) with status `PENDING`.

- `forceRefresh`: by default, a domain checked less than `jtypo.cache.ttl-hours` (24) ago, by any user,
  is taken from the shared cache. Set it to `true` to check every domain again.
- **400** `{"error": "…"}`: missing or invalid domain.
- **429** `{"error": "…"}` with `Retry-After`: too many simultaneous scans, or the daily quota is reached.
  Quotas are computed from the database, so they hold across instances and restarts.

### 3.2 List and read scans

- `GET /api/scans`: the 50 most recent scans of the user, newest first.
- `GET /api/scans/{id}`: one scan.

A scan of another user returns **403**, an unknown scan **404**.

### 3.3 Results, page by page

```http
GET /api/scans/{id}/results?after=0&limit=100
```

Returns the results ([§ 5.2](#52-scan-result)) whose `id` is greater than `after`, in increasing `id`
order. `limit` goes from 1 to 500 (default 100). To read everything, call again with `after` set to the
last `id` received.

### 3.4 Follow a scan live (SSE)

```http
GET /api/scans/{id}/events
Accept: text/event-stream
Last-Event-ID: 1234        (optional, sent automatically by browsers when they reconnect)
```

| Event | `id:` | Data | When |
|-------|-------|------|------|
| `result` | result id | one checked domain ([§ 5.2](#52-scan-result)) | for each result after `Last-Event-ID` |
| `progress` | — | the scan ([§ 5.1](#51-scan)) | at the start, then when the status or the counters change |
| `end` | — | the final scan | when the scan is finished; the stream is then closed |

- **Keep-alive**: a comment line (`: keep-alive`) is sent after 15 s without events.
- **Duration**: a stream lasts at most 30 minutes (`jtypo.sse.max-duration-ms`). The browser reconnects
  by itself and only receives the results it missed.
- **Proxies**: the response carries `Cache-Control: no-cache, no-transform` and `X-Accel-Buffering: no`,
  so that proxies neither compress nor buffer the stream.

### 3.5 Cancel a scan

`DELETE /api/scans/{id}` requires the CSRF header.

- **204**: the scan was active and is now cancelled. The worker stops at its next heartbeat (5 s).
- **409**: the scan was already finished.
- **403** / **404**: as above.

### 3.6 Screenshots

`GET /api/screenshots/{sha256}` returns the PNG with `Cache-Control: max-age=2592000, private, immutable`.
Results carry this URL in `screenshotUrl`; the image itself never travels in the events.

- **Storage**: screenshots are stored outside the database and addressed by the SHA-256 of their content,
  so a parking page shared by many domains is stored once.
- **Access**: a screenshot is served only to a user who owns a scan containing it. Otherwise the response
  is **404**, including for a malformed hash.

| Property | Environment variable | Description |
|----------|---------------------|-------------|
| `jtypo.storage.type` | `STORAGE_TYPE` | `filesystem` (default: desktop, development) or `s3` (web service) |
| `jtypo.storage.directory` | `STORAGE_DIRECTORY` | Directory of the `filesystem` storage |
| `jtypo.storage.s3.endpoint` | `S3_ENDPOINT` | Endpoint of an S3-compatible service (empty for AWS) |
| `jtypo.storage.s3.region` | `S3_REGION` | Region |
| `jtypo.storage.s3.bucket` | `S3_BUCKET` | Bucket (must exist) |
| `jtypo.storage.s3.access-key` / `secret-key` | `S3_ACCESS_KEY` / `S3_SECRET_KEY` | Credentials |
| `jtypo.storage.s3.path-style` | `S3_PATH_STYLE` | Path-style URLs (most self-hosted services) |

---

## 4. Desktop application endpoints (local mode only)

These endpoints serve the Swing client. They are loaded only when `jtypo.security.mode=local`. Responses
use the formats of [§ 5.3](#53-legacy-formats).

### 4.1 Generate and check

```http
GET /api/generate-and-check?domain=example.com
```

The response is an SSE stream of `domainUpdate` events, each with a `DomainResultDTO` and an `id:` set to
a timestamp in milliseconds.

1. One event per generated domain, with status `Testing...` and `httpCode` -1.
2. One event per checked domain, with its final status.
3. The stream is then closed.

An invalid domain gives `event: error` with `{"error": "Invalid domain name provided."}`.

### 4.2 Cancel

- `GET /api/cancel`: stops every running analysis; **200**.
- `DELETE /api/cancel-and-clear`: stops every analysis, then deletes all stored pages; **200**.

### 4.3 Stored pages

| Endpoint | Response |
|----------|----------|
| `GET /api/data/analyze?domain=` | **200** `DomainPageDTO`, or **500** with a text message |
| `GET /api/data/analyze-and-save?domain=` | SSE: one `domainUpdate` event (`DomainPageDTO`), or an `error` event |
| `GET /api/data/cached/{domain}` | **200** `DomainPageDTO`, or **404** with an empty body |
| `GET /api/data/all` | **200** array of `DomainPageDTO` |
| `DELETE /api/data/all` | **200** with the text `All cached data cleared` |
| `GET /api/data/stats` | **200** `{"totalDomains": 150}` |

Stored pages are emptied at each start of the application.

---

## 5. Data formats

### 5.1 Scan

```json
{
  "id": "3f0c9a2e-…",
  "baseDomain": "example.com",
  "status": "RUNNING",
  "totalDomains": 1780,
  "checkedDomains": 412,
  "createdAt": "2026-10-14T17:02:11.123Z",
  "startedAt": "2026-10-14T17:02:12.004Z",
  "finishedAt": null,
  "error": null
}
```

| Field | Description |
|-------|-------------|
| `baseDomain` | Scanned domain, Unicode form |
| `status` | `PENDING`, `RUNNING`, `COMPLETED`, `CANCELLED` or `FAILED` |
| `totalDomains` | Generated domains to check, at most `jtypo.quota.max-domains-per-scan` (50 000); 0 until the worker has generated them |
| `checkedDomains` | Domains checked so far |
| `error` | Reason of a `FAILED` scan |

### 5.2 Scan result

```json
{
  "id": 1234,
  "domain": "éxample.com",
  "domainAscii": "xn--xample-9ua.com",
  "status": "Suspicious",
  "httpCode": 200,
  "tlsStatus": "UNTRUSTED",
  "title": "Example",
  "language": "ENGLISH",
  "description": "…",
  "homepageText": "…",
  "httpHeaders": { "Server": "nginx" },
  "screenshotUrl": "/api/screenshots/9f86d081…",
  "checkedAt": "2026-10-14T17:02:15.456Z"
}
```

| Field | Description |
|-------|-------------|
| `id` | Order of the result in the scan; SSE event id |
| `domain` / `domainAscii` | Unicode form (display) / ASCII form (actual DNS name) |
| `status` | `Suspicious` (HTTP 200), `Safe` (other HTTP response), `Unreachable` (no response); see [Appendix A](#appendix-a-status-values) |
| `httpCode` | HTTP status after redirects; 0 if unreachable |
| `tlsStatus` | `VALID`, `EXPIRED`, `HOSTNAME_MISMATCH`, `UNTRUSTED`, `HANDSHAKE_FAILED` or `NONE` (no HTTPS response) |
| `title`, `description`, `homepageText` | From the page: title (up to 100 characters), meta description (up to 300), start of the text (about 200) |
| `language` | Detected language (12 languages), e.g. `ENGLISH`, or null |
| `screenshotUrl` | URL of the screenshot, or null |
| `checkedAt` | Date of the check (it may come from the shared cache) |

### 5.3 Legacy formats

The desktop endpoints use these formats.

**`DomainResultDTO`:**

| Field | Description |
|-------|-------------|
| `domain` | Domain |
| `status`, `title`, `language`, `description` | As in [§ 5.2](#52-scan-result); `status` is `Testing...` while a domain is checked |
| `httpCode` | -1 while testing |
| `tlsStatus` | As in [§ 5.2](#52-scan-result) |
| `homepageText` | Start of the page text |
| `httpHeaders` | Response headers |
| `screenshot`, `screenshotBase64` | The PNG in Base64 |

**`DomainPageDTO`:**

| Field | Description |
|-------|-------------|
| `domain` | Domain |
| `htmlContent`, `textContent` | Full HTML, up to 2 MB, and its text |
| `homepageText`, `title` | Start of the page text, page title |
| `metaDescription`, `metaKeywords`, `metaAuthor`, `metaOgTitle`, `metaOgDescription` | Page metadata |
| `detectedLanguage` | Detected language |
| `timestamp` | Epoch milliseconds |
| `httpCode`, `httpHeaders`, `tlsStatus` | Response |
| `screenshot` | PNG in Base64 |

---

## 6. Errors, rate limiting and quotas

### 6.1 HTTP status codes

| Code | When |
|------|------|
| 200 / 202 / 204 | Success (202: scan queued; 204: deletion or cancellation done) |
| 400 | Invalid domain (`{"error": "…"}`) |
| 401 | No valid session (web mode) |
| 403 | Missing CSRF token, or the scan belongs to another user |
| 404 | Unknown scan, screenshot not accessible, desktop endpoint in web mode |
| 409 | Cancelling a scan that is already finished |
| 429 | Rate limit or scan quota exceeded; see `Retry-After` |
| 500 | Server error (also when the identity provider is unreachable at login) |

Error bodies, when present, are JSON: `{"error": "message"}`. The legacy endpoints may return plain text
instead.

### 6.2 Rate limiting and quotas

Limits apply per user, or per client IP for anonymous calls. The rate limit is kept in memory, per API
instance. The scan quotas are computed from the database. A 429 response carries a `Retry-After` header
(seconds) and a JSON body `{"error": "..."}`. API responses carry `X-RateLimit-Remaining`.

| Property | Default | Description |
|----------|---------|-------------|
| `jtypo.rate-limit.requests-per-minute` | 120 | API requests per minute |
| `jtypo.quota.max-concurrent-scans` | 2 | Scans `PENDING` or `RUNNING` per user |
| `jtypo.quota.scans-per-day` | 50 | Scans per user over a sliding 24-hour window |
| `jtypo.quota.max-domains-per-scan` | 50 000 | Generated domains checked per scan (extra ones are dropped) |
| `jtypo.analysis.max-concurrent-checks` | 64 | Outbound checks running at the same time on an instance |
| `jtypo.outbound.user-agent` | `Mozilla/5.0 (compatible; JTypoSquatting/2.0)` | User-Agent of the checks and of the browser |

The desktop launcher raises the scan quotas (single trusted user).

To protect sign-up against bots, enable reCAPTCHA in the Keycloak registration flow (*Authentication →
registration → reCAPTCHA*, with the site keys of your domain).

---

## 7. Server-Sent Events

### 7.1 Protocol

Each event is a block of lines separated by an empty line:

```
id:1234
event:result
data:{"id":1234,"domain":"exmple.com",…}

: keep-alive

event:end
data:{"id":"3f0c…","status":"COMPLETED",…}
```

### 7.2 Browser client

```javascript
const source = new EventSource(`/api/scans/${id}/events`, { withCredentials: true });
const seen = new Set();
source.addEventListener("result", (e) => {
  const result = JSON.parse(e.data);
  if (seen.has(result.id)) return;      // may be sent again after a reconnection
  seen.add(result.id);
  addRow(result);
});
source.addEventListener("progress", (e) => showProgress(JSON.parse(e.data)));
source.addEventListener("end", (e) => { showProgress(JSON.parse(e.data)); source.close(); });
source.onerror = () => {
  // CLOSED: refused by the server (401, 403…). Otherwise the browser reconnects by itself.
  if (source.readyState === EventSource.CLOSED) showError();
};
```

### 7.3 Reconnection

On reconnection, the browser sends the id of the last event received in `Last-Event-ID`. The server only
sends the results with a greater id, so nothing is lost or repeated. A client that does not use
`EventSource` can do the same with the header, or read the missing results with
`GET /api/scans/{id}/results?after=…`.

---

## 8. Examples

### 8.1 cURL, desktop mode (no login)

```bash
curl -s -X POST localhost:8081/api/scans -H 'Content-Type: application/json' \
     -d '{"baseDomain":"example.com"}'
curl -N localhost:8081/api/scans/<id>/events
curl -s 'localhost:8081/api/scans/<id>/results?after=0&limit=500'
curl -s -X DELETE localhost:8081/api/scans/<id>
```

### 8.2 cURL, web mode (existing session)

Log in with a browser, then copy its `SESSION` and `XSRF-TOKEN` cookies:

```bash
COOKIES='SESSION=…; XSRF-TOKEN=…'
curl -s -b "$COOKIES" https://<site>/api/me
curl -s -b "$COOKIES" -H 'X-XSRF-TOKEN: <XSRF-TOKEN value>' -H 'Content-Type: application/json' \
     -d '{"baseDomain":"example.com"}' https://<site>/api/scans
curl -N -b "$COOKIES" https://<site>/api/scans/<id>/events
```

### 8.3 Python, reading every result page by page

```python
import requests

session = requests.Session()
session.cookies.update({"SESSION": "…"})
after, results = 0, []
while True:
    page = session.get(f"{base}/api/scans/{scan_id}/results",
                       params={"after": after, "limit": 500}).json()
    if not page:
        break
    results += page
    after = page[-1]["id"]
```

---

## Appendix A: Status values

| `status` | Meaning |
|----------|---------|
| `Suspicious` | The domain answers HTTP 200: a site is online |
| `Safe` | The domain answers with another HTTP status (redirect, 403, 404…) |
| `Unreachable` | No HTTP answer (unknown domain, refused connection, timeout, blocked destination) |
| `Testing...` | Being checked (desktop stream only) |
| `Active`, `Dead`, `Unknown` | Reserved |

## Appendix B: Monitoring endpoints

The `api` and `worker` instances expose these endpoints on port 8082, which the reverse proxy never
routes:

- `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`;
- `/actuator/info`;
- `/actuator/prometheus`.

See [DEPLOYMENT.md](DEPLOYMENT.md#monitoring).

## Appendix C: Changelog

| Version | Changes |
|---------|---------|
| 2.0-alpha1 (web service) | OpenID Connect login (PKCE, `SESSION` cookie, CSRF); persistent scans (`/api/scans`) processed by workers; per-scan SSE with `Last-Event-ID`; targeted cancellation; shared check cache; screenshots in S3-compatible storage (`/api/screenshots`); data export and account deletion (`/api/me`); rate limiting and quotas (429); TLS status of each domain; up to 50 000 domains per scan; desktop endpoints restricted to local mode |
| 2.0-alpha1 (desktop) | `/api/generate-and-check` SSE stream, `/api/data/*` stored pages, Swing client |
