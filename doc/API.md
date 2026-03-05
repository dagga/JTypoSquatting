# JTypoSquatting - API Documentation

**Version:** 2.0-alpha1  
**Last Updated:** March 2025  
**Base URL:** `http://localhost:8080/api`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication](#2-authentication)
3. [Domain Generation & Checking Endpoints](#3-domain-generation--checking-endpoints)
4. [Data Management Endpoints](#4-data-management-endpoints)
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

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
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

The API does not require authentication as it runs locally on the user's machine. Access is restricted to localhost by default.

**Security Note:** The API should only be accessible from localhost to prevent unauthorized access.

---

## 3. Domain Generation & Checking Endpoints

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

## 4. Data Management Endpoints

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
| 404 | Not Found | Domain not in cache |
| 500 | Internal Server Error | Server error |

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
