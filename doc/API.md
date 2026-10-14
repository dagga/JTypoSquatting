# JTypoSquatting - API Documentation

**Version:** 2.0-alpha1  
**Last Updated:** March 2025  
**Base URL:** `http://localhost:8080/api`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication](#2-authentication)
3. [Scans (web service)](#3-scans-web-service)
   - [Domain Generation & Checking Endpoints (desktop only)](#3bis-domain-generation--checking-endpoints-desktop-application-only)
4. [Data Management Endpoints (desktop only)](#4-data-management-endpoints-desktop-application-only)
5. [Data Transfer Objects](#5-data-transfer-objects)
6. [Error Handling](#6-error-handling)
7. [Server-Sent Events](#7-server-sent-events)
8. [Examples](#8-examples)

---

## 1. Overview

### 1.1 API Style

- **Protocol:** HTTP/1.1
- **Format:** JSON
- **Communication:** REST + Server-Sent Events (SSE)
- **Content Type:** `application/json`

### 1.2 Endpoints Summary

**Web service** (all modes; login required in web mode, see [Authentication](#2-authentication)):

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/me` | Current user |
| POST | `/scans` | Queue a scan (202) |
| GET | `/scans` | Last 50 scans of the user |
| GET | `/scans/{id}` | Scan status and counters |
| GET | `/scans/{id}/results?after=&limit=` | Results, page by page |
| GET | `/scans/{id}/events` | Live results (SSE, resumable with `Last-Event-ID`) |
| DELETE | `/scans/{id}` | Cancel a scan |

**Desktop application only** (`jtypo.security.mode=local`; these endpoints do not exist in web mode
because they act on the data and analyses of every user):

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/generate-and-check` | Generate and check domains (SSE) |
| GET | `/cancel` | Cancel every active analysis |
| DELETE | `/cancel-and-clear` | Cancel every analysis and clear the cache |
| GET | `/data/analyze-and-save` | Analyze domain and save (SSE) |
| GET | `/data/analyze` | Analyze single domain |
| GET | `/data/cached/{domain}` | Get cached domain data |
| GET | `/data/all` | Get all cached domains |
| DELETE | `/data/all` | Clear all cached data |
| GET | `/data/stats` | Get statistics |

--------|----------|-------------|------|
| GET | `/generate-and-check` | Generate and check domains (SSE) | No |
| GET | `/cancel` | Cancel active analysis | No |
| GET | `/data/analyze-and-save` | Analyze domain and save (SSE) | No |
| GET | `/data/analyze` | Analyze single domain | No |
| GET | `/data/cached/{domain}` | Get cached domain data | No |
| GET | `/data/all` | Get all cached domains | No |
| DELETE | `/data/all` | Clear all cached data | No |
| GET | `/data/stats` | Get statistics | No |

---

## 2. Authentication

The security mode is selected with `jtypo.security.mode`.

### 2.1 Web mode (`oidc`, default)

- Users log in through an OpenID Connect provider (Keycloak in development, see `docker-compose.yml`).
  Browsers are redirected to `/oauth2/authorization/oidc`; after login the backend keeps the session
  in an httpOnly cookie (`JSESSIONID`). The same cookie authenticates SSE streams.
- Every `/api/**` call without a valid session returns **401 Unauthorized** (no redirect).
- Requests that modify data (`POST`, `PUT`, `DELETE`) require the CSRF token: read the `XSRF-TOKEN`
  cookie and send it back in the `X-XSRF-TOKEN` header. Otherwise the response is **403 Forbidden**.
- `POST /logout` ends the session and the identity provider session.

| Property | Environment variable | Description |
|----------|---------------------|-------------|
| `jtypo.oidc.issuer-uri` | `OIDC_ISSUER_URI` | Issuer URL, discovered on first login |
| `jtypo.oidc.client-id` | `OIDC_CLIENT_ID` | Confidential client id |
| `jtypo.oidc.client-secret` | `OIDC_CLIENT_SECRET` | Client secret |
| `server.servlet.session.cookie.secure` | `SESSION_COOKIE_SECURE` | Must be `true` behind HTTPS |

#### GET /api/me

Returns the logged-in user.

```json
{ "id": "f3c1...", "username": "alice", "name": "Alice Dev", "email": "alice@example.com" }
```

Security headers on every response: `Content-Security-Policy` (`default-src 'none'`), `Referrer-Policy`,
`Permissions-Policy`, `X-Content-Type-Options`, `X-Frame-Options` and, over HTTPS, `Strict-Transport-Security`.
CORS is disabled: the frontend and the API are served from the same origin by the reverse proxy.
Behind the proxy, set `FORWARD_HEADERS_STRATEGY=framework` so that HTTPS and the public host are detected.

### 2.2 Desktop mode (`local`)

Set by the all-in-one JAR (`CombinedLauncher`): the API listens on `127.0.0.1` only and requires no
authentication. `GET /api/me` returns **204 No Content**. Never use this mode on a server.

---

## 3. Scans (web service)

A scan generates the typo variants of a domain and checks them. It is queued, processed by a worker
(possibly on another instance), and its results are stored, so the browser can be closed and the scan
followed again later.

### 3.0.1 Queue a scan

```http
POST /api/scans
Content-Type: application/json
X-XSRF-TOKEN: <XSRF-TOKEN cookie value>

{ "baseDomain": "example.com", "forceRefresh": false }
```

**202 Accepted**, `Location: /api/scans/{id}`:

```json
{
  "id": "3f0c9a2e-...", "baseDomain": "example.com", "status": "PENDING",
  "totalDomains": 0, "checkedDomains": 0,
  "createdAt": "2026-10-14T17:02:11.123Z", "startedAt": null, "finishedAt": null, "error": null
}
```

`forceRefresh`: by default, a domain checked less than `jtypo.cache.ttl-hours` (24) ago, by any user,
is taken from the shared cache instead of being checked again; `true` checks every domain again.

`status`: `PENDING`, `RUNNING`, `COMPLETED`, `CANCELLED`, `FAILED`. Errors: 400 (invalid domain),
429 (quota: simultaneous scans or scans per 24 hours, computed from the database).
Internationalised domains are accepted (`exämple.com`) and stored in punycode.

### 3.0.2 Follow a scan (SSE)

```http
GET /api/scans/{id}/events
Accept: text/event-stream
Last-Event-ID: 1234        (optional, sent automatically by browsers when they reconnect)
```

| Event | Data | Notes |
|-------|------|-------|
| `result` | one checked domain (see below) | `id:` = result id, used by `Last-Event-ID` |
| `progress` | the scan (same format as above) | sent when status or counters change |
| `end` | the final scan | the stream is then closed |

A comment line (`: keep-alive`) is sent every 15 s without activity. Streams last at most 30 minutes;
the browser reconnects by itself and only receives the results it missed.

```json
{
  "id": 1234, "domain": "ехаmple.com", "domainAscii": "xn--mple-43d4a.com",
  "status": "Suspicious", "httpCode": 200, "tlsStatus": "UNTRUSTED",
  "title": "Example", "language": "ENGLISH", "description": "...", "homepageText": "...",
  "httpHeaders": { "Server": "nginx" }, "screenshotUrl": "/api/screenshots/9f86d0…", "checkedAt": "2026-10-14T17:02:15.456Z"
}
```

### 3.0.3 Other operations

- `GET /api/scans/{id}/results?after=0&limit=100`: results with an id greater than `after` (max 500 per page).
- `DELETE /api/scans/{id}`: **204** if the scan was active and is cancelled, **409** if it was already finished.
- A scan of another user returns **403**, an unknown scan **404**.

### 3.0.4 Screenshots

`GET /api/screenshots/{sha256}` returns the PNG (`Cache-Control: private, immutable`). Screenshots are
stored outside the database, addressed by the SHA-256 of their content (a parking page shared by many
domains is stored once). A screenshot is served only to a user who owns a scan containing it; otherwise
**404**. Events carry the URL (`screenshotUrl`), never the image itself.

| Property | Environment variable | Description |
|----------|---------------------|-------------|
| `jtypo.storage.type` | `STORAGE_TYPE` | `filesystem` (default: desktop, development) or `s3` (web service) |
| `jtypo.storage.directory` | `STORAGE_DIRECTORY` | Directory of the `filesystem` store |
| `jtypo.storage.s3.endpoint` | `S3_ENDPOINT` | Endpoint of an S3-compatible service (empty for AWS) |
| `jtypo.storage.s3.region` | `S3_REGION` | Region |
| `jtypo.storage.s3.bucket` | `S3_BUCKET` | Bucket (must exist) |
| `jtypo.storage.s3.access-key` / `secret-key` | `S3_ACCESS_KEY` / `S3_SECRET_KEY` | Credentials |
| `jtypo.storage.s3.path-style` | `S3_PATH_STYLE` | Path-style URLs (most self-hosted services) |

Any S3-compatible service works (AWS S3, Scaleway, OVHcloud, Garage, SeaweedFS…). The community MinIO
images are no longer published on Docker Hub; the tests use S3Mock.

---

## 3bis. Domain Generation & Checking Endpoints (desktop application only)

These endpoints exist only in local mode (`jtypo.security.mode=local`). The web service uses [Scans](#3-scans-web-service).

### 3.1 Generate and Check Domains

Generates typo squatted domains and checks their status in real-time using Server-Sent Events.

```http
GET /api/generate-and-check?domain={domain}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `domain` | String | Yes | Target domain (e.g., `www.example.com`) |

**Response:**

- **Content-Type:** `text/event-stream`
- **Format:** Server-Sent Events stream

**SSE Event Format:**

```
event: domainUpdate
id: 1234567890
data: {"domain":"www.examp1e.com","status":"Testing...","httpCode":-1,...}

event: domainUpdate
id: 1234567891
data: {"domain":"www.examp1e.com","status":"Suspicious","httpCode":200,...}
```

**DomainResultDTO Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `domain` | String | Full domain URL |
| `status` | String | `Testing...`, `Suspicious`, `Safe`, `Dead`, `Timeout`, `Unreachable` |
| `title` | String | Page title |
| `language` | String | Detected language code |
| `description` | String | Page description |
| `httpCode` | Integer | HTTP response code (-1 = testing, 0 = dead) |
| `screenshot` | String (Base64) | Screenshot as Base64-encoded PNG |
| `httpHeaders` | Object | HTTP response headers |

**Example Stream:**

```javascript
// Event 1: Domain generated (testing)
event: domainUpdate
id: 1709567890123
data: {"domain":"www.examp1e.com","status":"Testing...","title":"","language":"","description":"","httpCode":-1,"screenshot":null,"httpHeaders":{}}

// Event 2: Check complete (suspicious)
event: domainUpdate
id: 1709567890456
data: {"domain":"www.examp1e.com","status":"Suspicious","title":"Example Domain","language":"EN","description":"Example website","httpCode":200,"screenshot":"iVBORw0KGgoAAAANSUhEUgAA...","httpHeaders":{"content-type":"text/html"}}
```

**Error Events:**

```
event: error
data: {"error":"Invalid domain name provided."}
```

---

### 3.2 Cancel Active Analysis

Cancels all ongoing domain analysis operations.

```http
GET /api/cancel
```

**Response:**

- **Status:** `200 OK`
- **Body:** Empty

**Use Case:** Stop domain generation when user clicks "Clear" or closes the application.

---

## 4. Data Management Endpoints (desktop application only)

These endpoints exist only in local mode: the cached data is shared by everyone.

### 4.1 Analyze and Save (SSE)

Analyzes a single domain and saves the result to the database with streaming updates.

```http
GET /api/data/analyze-and-save?domain={domain}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `domain` | String | Yes | Domain to analyze |

**Response:**

- **Content-Type:** `text/event-stream`
- **Format:** Server-Sent Events

**SSE Event:**

```
event: domainUpdate
data: {"domain":"www.example.com","status":"Safe","httpCode":200,...}
```

---

### 4.2 Analyze Single Domain

Analyzes a domain and returns the result immediately (no streaming).

```http
GET /api/data/analyze?domain={domain}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `domain` | String | Yes | Domain to analyze |

**Response:**

- **Status:** `200 OK` or `500 Internal Server Error`
- **Content-Type:** `application/json`

**Success Response:**

```json
{
  "domain": "www.example.com",
  "status": "Safe",
  "title": "Example Domain",
  "language": "EN",
  "description": "Example website description",
  "httpCode": 200,
  "screenshot": "iVBORw0KGgoAAAANSUhEUgAA...",
  "httpHeaders": {
    "content-type": "text/html; charset=utf-8",
    "server": "nginx"
  },
  "homepageText": "Example Domain. This domain is for use in...",
  "metaDescription": "Example website description",
  "metaKeywords": "example, test, domain",
  "metaAuthor": "Example Inc."
}
```

**Error Response:**

```json
{
  "error": "Error analyzing domain: Connection timeout"
}
```

---

### 4.3 Get Cached Domain

Retrieves cached analysis data for a specific domain.

```http
GET /api/data/cached/{domain}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `domain` | String (path) | Yes | Domain to retrieve (URL-encoded) |

**Response:**

- **Status:** `200 OK`, `404 Not Found`, or `500 Internal Server Error`

**Success (200 OK):**

```json
{
  "id": 1,
  "domain": "www.example.com",
  "status": "Safe",
  "title": "Example Domain",
  "language": "EN",
  "httpCode": 200,
  "timestamp": "2025-03-05T10:30:00Z"
}
```

**Not Found (404):**

```json
{
  "error": "Domain not found in cache"
}
```

---

### 4.4 Get All Cached Domains

Retrieves all cached domain analysis results.

```http
GET /api/data/all
```

**Response:**

- **Status:** `200 OK`
- **Content-Type:** `application/json`

```json
[
  {
    "id": 1,
    "domain": "www.example.com",
    "status": "Safe",
    "httpCode": 200
  },
  {
    "id": 2,
    "domain": "www.examp1e.com",
    "status": "Suspicious",
    "httpCode": 200
  }
]
```

---

### 4.5 Clear All Cached Data

Deletes all cached domain data from the database.

```http
DELETE /api/data/all
```

**Response:**

- **Status:** `200 OK`

```json
{
  "message": "All cached data cleared"
}
```

---

### 4.6 Get Statistics

Retrieves statistics about cached domains.

```http
GET /api/data/stats
```

**Response:**

- **Status:** `200 OK`

```json
{
  "totalDomains": 150
}
```

---

## 5. Data Transfer Objects

### 5.1 DomainResultDTO

Used for streaming domain check results.

```json
{
  "domain": "www.examp1e.com",
  "status": "Suspicious",
  "title": "Example Domain",
  "language": "EN",
  "description": "Example website for illustrative purposes",
  "httpCode": 200,
  "screenshotBase64": "iVBORw0KGgoAAAANSUhEUgAA...",
  "homepageText": "Example Domain. This domain is for use in...",
  "httpHeaders": {
    "content-type": "text/html; charset=utf-8",
    "content-length": "1234",
    "server": "nginx/1.18.0"
  }
}
```

**Field Details:**

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `domain` | String | No | Full domain URL |
| `status` | String | No | Current status |
| `title` | String | Yes | HTML page title |
| `language` | String | Yes | ISO language code |
| `description` | String | Yes | Page description/meta |
| `httpCode` | Integer | No | HTTP response code |
| `screenshotBase64` | String | Yes | Base64-encoded PNG |
| `homepageText` | String | Yes | First 200 chars of text |
| `httpHeaders` | Object | Yes | Response headers map |
| `tlsStatus` | String | No | Certificate check: `VALID`, `EXPIRED`, `HOSTNAME_MISMATCH`, `UNTRUSTED`, `HANDSHAKE_FAILED` or `NONE` (no HTTPS response) |

### 5.2 DomainPageDTO

Extended DTO for detailed domain analysis.

```json
{
  "domain": "www.example.com",
  "status": "Safe",
  "title": "Example Domain",
  "language": "EN",
  "description": "Example website",
  "httpCode": 200,
  "screenshotBase64": "iVBORw0KGgoAAAANSUhEUgAA...",
  "homepageText": "Example Domain...",
  "httpHeaders": {...},
  "metaDescription": "Meta description content",
  "metaKeywords": "keyword1, keyword2",
  "metaAuthor": "Author name",
  "metaOgTitle": "Open Graph title",
  "metaOgDescription": "Open Graph description",
  "detectedLanguage": "ENGLISH",
  "timestamp": "2025-03-05T10:30:00Z"
}
```

---

## 6. Error Handling

### 6.1 HTTP Status Codes

| Code | Meaning | When Returned |
|------|---------|---------------|
| 200 | OK | Successful request |
| 400 | Bad Request | Invalid parameters |
| 401 | Unauthorized | No valid session (web mode) |
| 403 | Forbidden | Missing CSRF token |
| 404 | Not Found | Domain not in cache |
| 429 | Too Many Requests | Rate limit or scan quota exceeded; see `Retry-After` |
| 500 | Internal Server Error | Server error |

#### Rate limiting and quotas

Limits apply per user (per client IP for anonymous calls). Counters are kept in memory, per instance.
A 429 response carries a `Retry-After` header (seconds) and a JSON body `{"error": "..."}`, also on the SSE endpoint.

| Property | Default | Description |
|----------|---------|-------------|
| `jtypo.rate-limit.requests-per-minute` | 120 | API requests per minute |
| `jtypo.quota.max-concurrent-scans` | 2 | Simultaneous scans per user |
| `jtypo.quota.scans-per-day` | 50 | Scans per user over a sliding 24-hour window |
| `jtypo.quota.max-domains-per-scan` | 3000 | Generated domains checked per scan (extra ones are dropped) |
| `jtypo.analysis.max-concurrent-checks` | 64 | Outbound checks running at the same time on the instance |
| `jtypo.outbound.user-agent` | `Mozilla/5.0 (compatible; JTypoSquatting/2.0)` | User-Agent of the HTTP client and the browser |

The desktop launcher raises the scan quotas (single trusted user).
To protect sign-up against bots, enable reCAPTCHA in the Keycloak registration flow
(*Authentication → registration → reCAPTCHA*, with the site keys of your domain).

### 6.2 Error Response Format

```json
{
  "error": "Error message description"
}
```

### 6.3 Common Errors

| Error | Cause | Resolution |
|-------|-------|------------|
| `Invalid domain name provided` | Malformed domain | Check domain format |
| `Cannot connect to API server` | Backend not running | Start backend service |
| `Analysis failed: Timeout` | Domain check timeout | Normal for dead domains |
| `Server error: Required files not found` | Missing algorithm files | Reinstall application |

---

## 7. Server-Sent Events

### 7.1 SSE Protocol

Server-Sent Events provide a unidirectional real-time communication channel from server to client.

**Connection:**

```http
GET /api/generate-and-check?domain=www.example.com
Accept: text/event-stream
Cache-Control: no-cache
```

**Server Response:**

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

event: domainUpdate
id: 1709567890123
data: {"domain":"www.examp1e.com",...}
```

### 7.2 Event Types

| Event Name | Description | Payload |
|------------|-------------|---------|
| `domainUpdate` | Domain status update | DomainResultDTO |
| `error` | Error occurred | Error message |

### 7.3 Client Implementation (JavaScript)

```javascript
const eventSource = new EventSource(
  'http://localhost:8080/api/generate-and-check?domain=www.example.com'
);

eventSource.addEventListener('domainUpdate', (event) => {
  const data = JSON.parse(event.data);
  console.log('Domain update:', data);
  // Update UI with new domain status
});

eventSource.addEventListener('error', (event) => {
  const error = JSON.parse(event.data);
  console.error('SSE Error:', error);
});

eventSource.onerror = () => {
  console.log('SSE connection closed');
  eventSource.close();
};
```

### 7.4 Reconnection

SSE automatically reconnects if the connection is lost. The server can control reconnection timing:

```
retry: 3000
```

---

## 8. Examples

### 8.1 cURL Examples

**Generate and Check Domains:**

```bash
curl -N "http://localhost:8080/api/generate-and-check?domain=www.example.com"
```

**Cancel Analysis:**

```bash
curl "http://localhost:8080/api/cancel"
```

**Analyze Single Domain:**

```bash
curl "http://localhost:8080/api/data/analyze?domain=www.example.com"
```

**Get Cached Domain:**

```bash
curl "http://localhost:8080/api/data/cached/www.example.com"
```

**Clear All Data:**

```bash
curl -X DELETE "http://localhost:8080/api/data/all"
```

### 8.2 Java RestClient Example

```java
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("http://localhost:8080/api/generate-and-check?domain=www.example.com"))
    .GET()
    .build();

client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
    .thenApply(response -> response.body())
    .forEach(line -> {
        if (line.startsWith("data:")) {
            String json = line.substring(5).trim();
            DomainResultDTO result = gson.fromJson(json, DomainResultDTO.class);
            // Process result
        }
    });
```

### 8.3 Python Example

```python
import requests

url = "http://localhost:8080/api/generate-and-check"
params = {"domain": "www.example.com"}

with requests.get(url, params=params, stream=True) as response:
    for line in response.iter_lines():
        if line.startswith(b"data:"):
            data = json.loads(line[5:].decode())
            print(f"Domain: {data['domain']}, Status: {data['status']}")
```

---

## Appendix A: Status Values

| Status | HTTP Code | Description |
|--------|-----------|-------------|
| `Testing...` | -1 | Check in progress |
| `Suspicious` | 200 | Active with similar content |
| `Safe` | 3xx-5xx | Active with different content |
| `Dead` | 0 | No response |
| `Timeout` | N/A | Check timed out |
| `Unreachable` | N/A | Network error |

---

## Appendix B: Homoglyph Mappings

See [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md#homoglyph-mappings) for complete character mapping table.

---

## Appendix C: Changelog

| Version | Date | Changes |
|---------|------|---------|
| 2.0-alpha1 | March 2025 | Initial API documentation |

---

*End of Document*
