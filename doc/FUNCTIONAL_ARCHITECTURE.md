# JTypoSquatting - Functional Architecture

**Version:** 2.0-alpha1
**Last Updated:** October 2026 (branch `feature/web-public`)

---

## Table of Contents

1. [Purpose and Scope](#1-purpose-and-scope)
2. [Users](#2-users)
3. [Functional Overview](#3-functional-overview)
4. [Use Cases of the Web Service](#4-use-cases-of-the-web-service)
5. [Business Rules](#5-business-rules)
6. [Desktop Application](#6-desktop-application)
7. [Error Handling](#7-error-handling)

---

## 1. Purpose and Scope

JTypoSquatting finds the domains that imitate a given domain. It does three things:
1. It **generates** the plausible typo variants of a domain (21 algorithms, see
   [Technical Reference § 2](TECHNICAL_REFERENCE.md#2-domain-generation-algorithms)).
2. It **checks** which variants are online and what they host.
3. It **presents** the results live, with a screenshot of each site.

The product exists in two forms:

| | Web service (main form) | Desktop application |
|---|---|---|
| Users | Several, with an account | One, on their machine |
| Interface | Browser, French / English | Swing window |
| Scans | Queued, processed by workers, kept for 90 days | Processed while the window is open |
| Data | MySQL + S3 storage | In memory |

This document describes **what** the product does. How it does it is described in
[ARCHITECTURE.md](ARCHITECTURE.md) and [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md), and the API in
[API.md](API.md).

---

## 2. Users

| Persona | Needs |
|---------|-------|
| **Security analyst** (SOC, CERT) | Detect phishing domains imitating the company before campaigns start; assess a site without visiting it; hand the list over to takedown teams |
| **Brand protection / legal officer** | Inventory the domains infringing a trademark; keep evidence (screenshot, date, certificate); export it |
| **Visitor** (not logged in) | Understand what the service does and how (public pages) |

---

## 3. Functional Overview

```
Visitor ──► Public pages: home, how it works, FAQ, legal notice, terms, privacy
   │
   └─ Log in (OpenID Connect)
        │
        ▼
User ──► My scans
          ├─ Start a scan ──────────► queued ──► worker: generate → check → store
          ├─ Follow a scan live ◄──────────── results streamed as they are stored
          │    ├─ counters, filter, details, screenshot
          │    ├─ open a site (after a warning)
          │    ├─ cancel
          │    └─ export CSV / JSON
          ├─ History of scans
          └─ My data: export (JSON), delete my account
```

---

## 4. Use Cases of the Web Service

### UC-1 Log in / log out

- **Actor:** visitor.
- **Flow:**
  1. The visitor clicks **Log in**.
  2. The browser goes to the identity provider (Keycloak or another OpenID Connect provider), with PKCE.
  3. After authentication, the user is back on **My scans**, with a session cookie (8 hours of
     inactivity).
- **Log out:** the session ends, and so does the identity provider session (otherwise the next login
  would be silent).
- **Failed login:** the user is back on My scans, with an explanatory message and the login button. The
  login is not retried automatically.
- **Account:** created at the first scan, from the profile given by the provider (identifier, user name,
  e-mail).

### UC-2 Start a scan

- **Actor:** logged-in user.
- **Input:** a domain name. Internationalised names are accepted (`exämple.com`). An option
  *Ignore the cache* checks every domain again.
- **Flow:**
  1. The service validates the name and checks the quotas (§ 5.2).
  2. It queues the scan (*Pending*) and opens its page.
  3. A worker takes it over (*Running*), generates the variants (about 1 800 per domain today, at most
     50 000), then checks them.
- **Errors:** invalid name (message under the field), quota reached (message with the delay before the
  next attempt).

### UC-3 Follow a scan live

- **Actor:** owner of the scan.
- **Live updates:** results arrive as they are stored, together with the status, the progress
  (*checked / total*) and four counters: *Checked*, *Online*, *Suspicious*, *Unreachable*.
- **Leaving the page:** the scan goes on. When the user comes back, the results already stored are sent
  again, then the next ones.
- **After a network outage:** the browser reconnects by itself and only receives the missed results.
- **Filters:**
  - a text filter on the domain (Unicode or punycode form) and the page title;
  - an *Online only* option.
- **Volume:** the table stays fluid up to the 50 000 results of a scan; only the visible rows are drawn.

### UC-4 Inspect a result

- **Actor:** owner of the scan.
- **Content of the detail panel:**
  - the domain and its technical form (punycode) when they differ;
  - the status, the HTTP code and the TLS certificate state (*Valid*, *Expired*, *Wrong name*,
    *Untrusted*, *TLS failure*);
  - the screenshot;
  - the title, the description and the start of the page;
  - the HTTP headers and the date of the check.
- **Screenshots:** only shown to users who own a scan containing them.

### UC-5 Open a site

- **Actor:** owner of the scan.
- **Flow:**
  1. *Open the site…* first shows a warning: the site may host phishing or malware, the screenshot is
     usually enough, and no information should be typed on it.
  2. Only after confirmation does the site open, in a new tab, without referrer.

### UC-6 Cancel a scan

- **Actor:** owner of a pending or running scan.
- **Result:** the scan becomes *Cancelled*. The worker stops at its next heartbeat (within 5 seconds),
  and the results already stored are kept.

### UC-7 Export results

- **Formats:** CSV or JSON, of the **currently filtered** results.
- **Content:** domain, punycode form, status, HTTP code, TLS state, title, language, description, date.
- **CSV:** UTF-8 with a BOM, for spreadsheets. Cells that a spreadsheet would run as formulas (starting
  with `=`, `+`, `-` or `@`) are neutralised, because titles come from hostile sites.

### UC-8 History

The 50 most recent scans of the user. Each one shows its domain, status, progress and date, and links to
its page. The list refreshes itself while a scan is in progress.

### UC-9 Exercise GDPR rights

- **Export:** *My data › Export my data* downloads in JSON everything stored about the user: profile,
  scans and results.
- **Deletion:** *Delete my account*, after confirmation, deletes:
  - the profile, the scans and their results;
  - the screenshots no other user needs;
  - the user's other sessions.

  The user is then logged out, including at the identity provider. The account at the identity provider
  itself is managed there.

### UC-10 Read the public pages

Home, *How it works* (the families of algorithms and the checks), FAQ, legal notice, terms of use and
privacy. They are available in French (`/fr`) and English (`/en`); the language comes from the browser
and can be switched on every page. These pages are indexed by search engines; the application pages are
not.

---

## 5. Business Rules

### 5.1 Result status

| Observation | Status | Displayed as |
|-------------|--------|--------------|
| HTTP 200 | `SUSPICIOUS` | *Suspicious* (red) |
| Other HTTP response (redirect, 403…) | `SAFE` | *Online* (amber) |
| No response: unknown domain, refused, timeout, forbidden destination | `UNREACHABLE` | *Unreachable* (grey) |

The TLS state is independent of the status. An invalid certificate is a signal of a fraudulent site; the
page is then read over plain HTTP.

### 5.2 Quotas and fair use

| Rule | Default |
|------|---------|
| Active scans per user | 2 |
| Scans per user over 24 sliding hours | 50 |
| API requests per minute and per user | 120 |
| Domains checked per scan | 50 000 |

Exceeding a quota gives a message and the delay before the next attempt (HTTP 429). The desktop
application raises the scan quotas, since it has a single trusted user.

### 5.3 Shared checks

A domain checked less than 24 hours ago, by any user, is not checked again: its result is reused. Each
scan keeps a **snapshot** of its results, so refreshing a check does not change the history of earlier
scans. *Ignore the cache* forces new checks.

### 5.4 Protection of third parties and of the service

- The service only visits public addresses, on ports 80 and 443. A generated domain that points to an
  internal address is reported *Unreachable*.
- It identifies itself with its User-Agent.
- It limits its simultaneous checks.
- Screenshots are taken in an isolated browser.

### 5.5 Retention

| Data | Kept |
|------|------|
| Finished scans and their results | 90 days |
| Shared checks | 30 days |
| Screenshots | As long as a result or a check references them |
| Account | Until the user deletes it |

Running scans are never purged.

---

## 6. Desktop Application

`java -jar JTypoSquatting.jar` starts the backend in **local mode** (on 127.0.0.1, no login) and opens
the Swing window.

| Function | Behaviour |
|----------|-----------|
| Scan | Type a domain and start: generation and checks of all variants, results streamed into the table |
| Counters | Generated, online, unreachable |
| Preview | Screenshot of the selected domain |
| Details | Page information; *Open in Browser* asks for a confirmation first |
| Cancel / Clear | Cancel the analysis; clear the table and the local data |
| Logs | Backend and client log viewer |
| Language | French / English |

Results are not kept after the application closes. The desktop application uses the legacy endpoints
(`/api/generate-and-check`, `/api/data/**`, `/api/cancel*`), which exist in local mode only. Retiring the
Swing client is a pending product decision ([action plan](ACTION_PLAN.md), P3.7).

---

## 7. Error Handling

| Situation | What the user sees |
|-----------|--------------------|
| Invalid domain | Message under the field; nothing is queued |
| Quota reached | Message with the delay before the next attempt |
| Not logged in, or session expired | Invitation to log in |
| Login refused by the identity provider | Error message and login button, no automatic retry |
| Scan of another user / unknown scan | *This scan does not belong to you* / *Scan not found* |
| Live connection lost | Automatic reconnection; a message if the server refuses the stream |
| Worker failure | The scan is resumed by another worker after 2 minutes, without checking the same domains again |
| Unexpected processing error | The scan ends as *Failed*, with the error |
| Site that cannot be captured | Detail panel without a screenshot (*No screenshot for this domain*) |
