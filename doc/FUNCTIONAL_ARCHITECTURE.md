# JTypoSquatting - Functional Architecture Documentation

**Version:** 2.0-alpha1  
**Last Updated:** March 2025  
**Status:** Draft

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [User Personas](#2-user-personas)
3. [Functional Overview](#3-functional-overview)
4. [Use Cases](#4-use-cases)
5. [User Interface Functions](#5-user-interface-functions)
6. [Business Logic Functions](#6-business-logic-functions)
7. [Data Functions](#7-data-functions)
8. [External Interface Functions](#8-external-interface-functions)
9. [State Management](#9-state-management)
10. [Error Handling](#10-error-handling)
11. [Configuration Functions](#11-configuration-functions)
12. [Appendices](#12-appendices)

---

## 1. Introduction

### 1.1 Purpose

This document describes the functional architecture of JTypoSquatting, detailing the system's capabilities, user interactions, and business processes from a functional perspective.

### 1.2 Scope

The functional architecture covers all user-visible features, business logic, data management, and external integrations of the JTypoSquatting application.

### 1.3 Relationship to Other Documents

- **Software Architecture** (ARCHITECTURE.md): Technical implementation details
- **API Documentation** (API.md): REST API specifications
- **User Guide**: End-user documentation (separate document)

---

## 2. User Personas

### 2.1 Primary Persona: Security Analyst

```
┌─────────────────────────────────────────────────────────────┐
│                    Security Analyst                         │
├─────────────────────────────────────────────────────────────┤
│  Role: Cybersecurity professional investigating potential   │
│        typo squatting domains for brand protection          │
│                                                             │
│  Goals:                                                     │
│  • Identify potential typo squatted domains                 │
│  • Verify which domains are active                          │
│  • Collect evidence (screenshots, metadata)                 │
│  • Export results for further analysis                      │
│                                                             │
│  Technical Level: Intermediate to Advanced                  │
│  Usage Frequency: Regular (daily/weekly)                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Secondary Persona: Brand Protection Officer

```
┌─────────────────────────────────────────────────────────────┐
│               Brand Protection Officer                      │
├─────────────────────────────────────────────────────────────┤
│  Role: Non-technical user responsible for monitoring        │
│        brand misuse online                                  │
│                                                             │
│  Goals:                                                     │
│  • Quick domain generation and checking                     │
│  • Clear visual indicators of threat level                  │
│  • Easy-to-understand reports                               │
│                                                             │
│  Technical Level: Basic                                     │
│  Usage Frequency: Occasional                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Functional Overview

### 3.1 Functional Decomposition

```
┌─────────────────────────────────────────────────────────────────┐
│                    JTypoSquatting Functions                     │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
┌───────▼────────┐   ┌────────▼────────┐   ┌──────▼───────┐
│   Domain       │   │   Analysis      │   │   Data       │
│   Generation   │   │   & Checking    │   │   Management │
└───────┬────────┘   └────────┬────────┘   └──────┬───────┘
        │                     │                     │
   ┌────┴────┐           ┌────┴────┐           ┌────┴────┐
   │         │           │         │           │         │
   ▼         ▼           ▼         ▼           ▼         ▼
┌──────┐ ┌──────┐   ┌──────┐ ┌──────┐   ┌──────┐ ┌──────┐
│ Dash │ │ Homo │   │ HTTP │ │ Screen│  │ Save │ │Export│
│ Gen  │ │ Gen  │   │ Check│ │ shot  │  │  DB  │ │ CSV  │
└──────┘ └──────┘   └──────┘ └──────┘   └──────┘ └──────┘
```

### 3.2 Function Categories

| Category | Functions | Priority |
|----------|-----------|----------|
| **Core** | Domain generation, HTTP checking, Status display | High |
| **Analysis** | Screenshot capture, Language detection, Metadata extraction | High |
| **Data** | Database storage, Export, Clear data | Medium |
| **UI/UX** | Real-time updates, Copy to clipboard, Search/filter | Medium |
| **Configuration** | API settings, Language selection, Log viewing | Low |

---

## 4. Use Cases

### 4.1 Use Case Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         JTypoSquatting                          │
│                                                                 │
│  ┌──────────────┐                                              │
│  │ Security     │                                              │
│  │ Analyst      │                                              │
│  └──────┬───────┘                                              │
│         │                                                      │
│    ┌────┴────────────────────────────────────────────┐        │
│    │                                                 │        │
│    ▼                                                 ▼        │
│  ┌─────────────────┐                           ┌───────────┐  │
│  │ Generate Domains│                           │ View Logs │  │
│  └────────┬────────┘                           └───────────┘  │
│           │                                                    │
│    ┌──────┴──────┐                                            │
│    │             │                                            │
│    ▼             ▼                                            │
│  ┌─────────┐ ┌──────────┐   ┌────────────┐   ┌────────────┐  │
│  │ Check   │ │ View     │   │  Export    │   │  Clear     │  │
│  │ Domains │ │ Results  │   │  Results   │   │  Data      │  │
│  └────┬────┘ └────┬─────┘   └────────────┘   └────────────┘  │
│       │          │                                           │
│       │    ┌─────┴─────┐                                     │
│       │    │           │                                     │
│       ▼    ▼           ▼                                     │
│  ┌──────────┐   ┌──────────────┐                             │
│  │ Capture  │   │ Open Domain  │                             │
│  │Screenshot│   │ in Browser   │                             │
│  └──────────┘   └──────────────┘                             │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### 4.2 Detailed Use Cases

#### UC-001: Generate and Check Domains

| Attribute | Description |
|-----------|-------------|
| **ID** | UC-001 |
| **Name** | Generate and Check Domains |
| **Actor** | Security Analyst |
| **Precondition** | Application is running, backend is connected |
| **Trigger** | User enters domain and clicks "Generate" |

**Main Flow:**
1. User enters a domain name (e.g., www.example.com)
2. User clicks the "Generate" button
3. System generates typo squatted variants using all algorithms
4. System displays all generated domains with "Testing..." status
5. System checks each domain in parallel (HTTP + screenshot)
6. System updates each domain's status in real-time
7. System saves results to database
8. System displays completion message with statistics

**Alternate Flows:**
- A1: Invalid domain format → Show error message
- A2: Backend not connected → Show connection error
- A3: User cancels operation → Stop all pending checks

**Postcondition:** Generated domains are displayed with their status and saved to database

---

#### UC-002: View Domain Details

| Attribute | Description |
|-----------|-------------|
| **ID** | UC-002 |
| **Name** | View Domain Details |
| **Actor** | Security Analyst |
| **Precondition** | At least one domain has been checked |
| **Trigger** | User double-clicks a domain row |

**Main Flow:**
1. User selects a domain from the results table
2. User double-clicks or selects "View Details"
3. System opens a detail dialog showing:
   - Full domain URL
   - Status indicator (color-coded)
   - HTTP response code and headers
   - Page title and description
   - Detected language
   - Screenshot preview
   - Meta tags (description, keywords, author)
4. User can open the domain in browser from the dialog

**Postcondition:** Domain details are displayed in a separate window

---

#### UC-003: Export Results

| Attribute | Description |
|-----------|-------------|
| **ID** | UC-003 |
| **Name** | Export Results |
| **Actor** | Security Analyst |
| **Precondition** | Results are available in the table |
| **Trigger** | User selects domains and copies to clipboard |

**Main Flow:**
1. User selects one or more domains in the results table
2. User clicks "Copy" button or presses Ctrl+C
3. System copies selected domain URLs to clipboard
4. System displays confirmation message

**Postcondition:** Selected domains are copied to system clipboard

---

#### UC-004: Clear All Data

| Attribute | Description |
|-----------|-------------|
| **ID** | UC-004 |
| **Name** | Clear All Data |
| **Actor** | Security Analyst |
| **Precondition** | Application is running |
| **Trigger** | User clicks "Clear" button |

**Main Flow:**
1. User clicks "Clear" button
2. System stops any ongoing domain checks
3. System clears the results table
4. System clears the database
5. System clears log panels
6. System resets status to "Ready"

**Postcondition:** All data is cleared, application is ready for new analysis

---

## 5. User Interface Functions

### 5.1 Main Window Layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Aleph TypoSquatting Tool                                              [_][□][X]│
├─────────────────────────────────────────────────────────────────────────────┤
│  Domain Name: [www.example.com                    ] [⟳ Generate]            │
├──────────────────────────────────────────────┬──────────────────────────────┤
│                                              │  Page Preview                │
│  ┌────────────────────────────────────────┐ │  ┌──────────────────────┐    │
│  │ Domain URL │Status│Title │...│HTTP│   │ │  │                      │    │
│  ├────────────────────────────────────────┤ │  │   [Screenshot]       │    │
│  │ www.examp1e.com │🔴│...│...│200│      │ │  │                      │    │
│  │ www.exampIe.com │🟢│...│...│301│      │ │  │                      │    │
│  │ www.examp1e.com │🟠│...│...│---│      │ │  │                      │    │
│  └────────────────────────────────────────┘ │  └──────────────────────┘    │
│                                              │                              │
│  [Backend] [Frontend]                        │                              │
│  ┌────────────────────────────────────────┐ │                              │
│  │ Log output...                          │ │                              │
│  └────────────────────────────────────────┘ │                              │
├──────────────────────────────────────────────┴──────────────────────────────┤
│  Generated: 150 | HTTP up: 45 | Inaccessible: 105   [Copy] [Clear]          │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 UI Component Functions

| Component | Function | Interaction |
|-----------|----------|-------------|
| **Domain Input Field** | Enter target domain | Text input |
| **Generate Button** | Start domain generation | Click |
| **Results Table** | Display generated domains | Sort, Select, Double-click |
| **Status LED** | Visual status indicator | Color-coded (Red/Green/Orange) |
| **HTTP Code Column** | Show HTTP response | Numeric display |
| **Screenshot Panel** | Preview webpage | Auto-update on selection |
| **Log Tabs** | View backend/frontend logs | Tab switching |
| **Copy Button** | Export selected domains | Click |
| **Clear Button** | Reset all data | Click |
| **Metrics Display** | Show statistics | Auto-update |

### 5.3 Status Indicators

| Status | Color | Icon | Meaning |
|--------|-------|------|---------|
| **Suspicious** | Red | 🔴 | Domain is active with similar content |
| **Safe** | Green | 🟢 | Domain is active but different content |
| **Testing** | Orange | 🟠 | Domain check in progress |
| **Dead** | Gray | ⚫ | Domain is not responding |
| **Timeout** | Gray | ⚫ | Check timed out |

---

## 6. Business Logic Functions

### 6.1 Domain Generation Functions

#### F-GEN-001: Dash Generation

```
Function: generateDashVariants(domain: String) → List<String>

Input:  www.example.com
Output: [www.example-com.com, www-example.com, wwwexample.com, ...]

Rules:
1. Add hyphen between each character pair
2. Remove existing hyphens
3. Move hyphens to different positions
4. Combine with TLD variations
```

#### F-GEN-002: Homoglyph Generation

```
Function: generateHomoglyphVariants(domain: String) → List<String>

Input:  www.example.com
Output: [www.ехаmple.com, www.examp1e.com, www.exampIe.com, ...]

Character Mappings:
- a → а (Cyrillic), ɑ (Latin alpha)
- e → е (Cyrillic), ɛ (Latin epsilon)
- i → і (Cyrillic), ɩ (Latin iota)
- l → 1 (digit), I (uppercase i)
- o → 0 (digit), ο (Greek omicron)
- ... (full mapping table in Technical Reference)
```

#### F-GEN-003: Misspell Generation

```
Function: generateMisspellVariants(domain: String) → List<String>

Input:  www.absence.com
Output: [www.absense.com, www.abscence.com, www.absences.com, ...]

Rules:
1. Apply common English spelling mistakes
2. Double/swap adjacent letters
3. Drop silent letters
4. Phonetic substitutions
```

#### F-GEN-004: TLD Generation

```
Function: generateTLDVariants(domain: String) → List<String>

Input:  www.example.eu
Output: [www.example.fr, www.example.com, www.example.eu.com, ...]

Rules:
1. Replace TLD with common alternatives (.com, .net, .org, .fr, etc.)
2. Add TLD as suffix (example.eu → example.eu.com)
3. Create company-name subdomain variants
```

### 6.2 Domain Checking Functions

#### F-CHK-001: HTTP Status Check

```
Function: checkHttpStatus(domain: String) → DomainResultDTO

Process:
1. Send HTTP GET request to https://domain
2. Record response code
3. Record response headers
4. Handle exceptions:
   - UnknownHostException → HTTP code 0 (dead)
   - SocketTimeoutException → HTTP code 0 (dead)
   - Other exceptions → HTTP code 0 (unreachable)

Status Determination:
- HTTP 200 → "Suspicious" (exact match likely)
- HTTP 3xx → "Safe" (redirect, different content)
- HTTP 4xx/5xx → "Safe" (error page)
- HTTP 0 → "Dead" (no response)
```

#### F-CHK-002: Screenshot Capture

```
Function: captureScreenshot(domain: String) → byte[]

Process:
1. Create JavaFX WebView (headless)
2. Load domain URL
3. Wait for page load + JavaScript execution (3 seconds)
4. Capture rendered image
5. Resize to 320x240
6. Encode as PNG
7. Return byte array

Conditions:
- Only capture if HTTP code = 200
- Timeout after 10 seconds
- Handle JavaScript errors gracefully
```

#### F-CHK-003: Language Detection

```
Function: detectLanguage(content: String) → String

Process:
1. Extract text content from HTML
2. Use Lingua library for detection
3. Support 75+ languages
4. Return ISO language code

Languages Supported:
- European: EN, FR, DE, ES, IT, PT, NL, RU, PL, etc.
- Asian: ZH, JA, KO, AR, HI, TH, VI, etc.
- Other: SW, YOR, ZU, etc.
```

### 6.3 Status Determination Logic

```
┌─────────────────────────────────────────────────────────────┐
│              Domain Status Decision Tree                    │
└─────────────────────────────────────────────────────────────┘

                    Start
                      │
                      ▼
              ┌───────────────┐
              │ HTTP Check    │
              └───────┬───────┘
                      │
         ┌────────────┼────────────┐
         │            │            │
         ▼            ▼            ▼
    HTTP > 0     HTTP = 0    Timeout
         │            │            │
         │            ▼            ▼
         │        ┌──────┐    ┌────────┐
         │        │ Dead │    │ Remove │
         │        └──────┘    └────────┘
         │
         ▼
    ┌─────────────┐
    │ HTTP = 200? │
    └──────┬──────┘
           │
     ┌─────┴─────┐
     │           │
    Yes         No
     │           │
     ▼           ▼
┌─────────┐ ┌─────────┐
│Suspicious│ │  Safe   │
│ (Red)   │ │ (Green) │
└─────────┘ └─────────┘
```

---

## 7. Data Functions

### 7.1 Data Storage Functions

#### F-DATA-001: Save Domain Result

```
Function: saveDomainResult(result: DomainResultDTO) → Boolean

Data Stored:
- Domain URL (unique key)
- Status (Suspicious/Safe/Dead/Testing)
- HTTP code and headers
- Page title and description
- Detected language
- Screenshot (BLOB)
- Meta tags
- Timestamp

Storage: H2 embedded database
Constraint: Unique domain (upsert behavior)
```

#### F-DATA-002: Retrieve Cached Results

```
Function: getCachedDomain(domain: String) → Optional<DomainResultDTO>

Process:
1. Query database by domain URL
2. Return result if exists
3. Return empty if not found

Use Case: Skip re-checking recently analyzed domains
```

#### F-DATA-003: Clear All Data

```
Function: clearAllData() → Boolean

Process:
1. Delete all records from domain_results table
2. Reset auto-increment counter
3. Clear in-memory caches
4. Return success status
```

### 7.2 Data Export Functions

#### F-EXP-001: Copy to Clipboard

```
Function: copyToClipboard(domains: List<String>) → Boolean

Process:
1. Join domain URLs with newline
2. Copy to system clipboard
3. Return success status

Format: Plain text, one domain per line
```

---

## 8. External Interface Functions

### 8.1 HTTP/HTTPS Interface

#### F-EXT-001: Domain HTTP Check

```
Function: httpGet(domain: String) → HttpResponse

Request:
- Method: GET
- URL: https://{domain} or http://{domain}
- Timeout: 10 seconds (connect), 30 seconds (read)
- User-Agent: Mozilla/5.0 (compatible; JTypoSquatting/2.0)

Response:
- Status code
- Headers
- Body (for analysis)

Error Handling:
- DNS failure → Exception
- Timeout → Exception
- SSL error → Continue with HTTP
```

#### F-EXT-002: Screenshot Capture

```
Function: renderPage(domain: String) → Image

Process:
1. JavaFX WebView loads URL
2. Wait for document complete
3. Additional 3-second wait for JavaScript
4. Capture viewport image

External Dependencies:
- Target website must be accessible
- JavaScript may modify DOM
- CSS affects rendering
```

### 8.2 DNS Interface

#### F-EXT-003: Domain Resolution

```
Function: resolveDomain(domain: String) → Optional<InetAddress>

Process:
1. Java InetAddress.getByName()
2. Returns IP if resolvable
3. Returns null if unknown host

Use: Early detection of dead domains
```

---

## 9. State Management

### 9.1 Application States

```
┌─────────────────────────────────────────────────────────────┐
│                  Application State Machine                  │
└─────────────────────────────────────────────────────────────┘

     ┌──────────┐
     │  Ready   │◄────────────────────────┐
     └────┬─────┘                         │
          │                               │
          │ Click Generate                │ Complete/Cancel
          ▼                               │
     ┌──────────┐                         │
     │Generating│─────────────────────────┘
     └────┬─────┘
          │
          │ Domain checks
          ▼
     ┌──────────┐
     │ Checking │───────► Update UI (real-time)
     └──────────┘
```

### 9.2 Domain States

| State | HTTP Code | Description | Transition |
|-------|-----------|-------------|------------|
| **Testing...** | -1 | Check in progress | → Suspicious/Safe/Dead |
| **Suspicious** | 200 | Active, similar content | Terminal |
| **Safe** | 3xx-5xx | Active, different content | Terminal |
| **Dead** | 0 | No response | Terminal |
| **Timeout** | N/A | Check timed out | → Removed |
| **Unreachable** | N/A | Network error | → Removed |

### 9.3 Counter Management

```
┌─────────────────────────────────────────────────────────────┐
│                    Metrics Counters                         │
└─────────────────────────────────────────────────────────────┘

Counters:
- totalGeneratedCount: Incremented when domain added
- activeDomainCount: Domains with HTTP > 0 (Suspicious/Safe)
- deadDomainCount: Domains with HTTP = 0 (Dead)

Update Rules:
1. Testing... → Not counted in active/dead
2. Testing... → Suspicious: activeCount++
3. Testing... → Safe: activeCount++
4. Testing... → Dead: deadCount++
5. Active → Dead: activeCount--, deadCount++
6. Dead → Active: deadCount--, activeCount++
```

---

## 10. Error Handling

### 10.1 Error Categories

| Category | Examples | Handling |
|----------|----------|----------|
| **Input Errors** | Invalid domain format | Show dialog, highlight input |
| **Connection Errors** | Backend not reachable | Show error, retry option |
| **Network Errors** | DNS failure, timeout | Mark domain as dead |
| **Processing Errors** | Screenshot failure | Continue without screenshot |
| **System Errors** | Out of memory | Log error, graceful degradation |

### 10.2 Error Recovery

```
┌─────────────────────────────────────────────────────────────┐
│                    Error Recovery Flow                      │
└─────────────────────────────────────────────────────────────┘

Error Detected
      │
      ▼
┌─────────────┐
│ Log Error   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Classify    │
│ Error Type  │
└──────┬──────┘
       │
   ┌───┴───┬───────────┬─────────────┐
   │       │           │             │
   ▼       ▼           ▼             ▼
┌──────┐ ┌──────┐ ┌──────────┐ ┌──────────┐
│Retry │ │Skip │ │Show Error│ │Shutdown  │
│      │ │Item │ │ Dialog   │ │Gracefully│
└──────┘ └──────┘ └──────────┘ └──────────┘
```

### 10.3 Specific Error Handling

#### E-001: Backend Connection Failure

```
Condition: Cannot connect to localhost:8080
Action: Show dialog "Cannot connect to API server"
Recovery: User must start backend manually
```

#### E-002: Domain Check Timeout

```
Condition: No response after 5 seconds
Action: Mark domain as "Timeout", remove from list
Recovery: Continue with other domains
```

#### E-003: Screenshot Failure

```
Condition: JavaFX WebView error
Action: Store null screenshot, continue
Recovery: Domain still usable without screenshot
```

---

## 11. Configuration Functions

### 11.1 User-Configurable Settings

| Setting | Default | Range | Location |
|---------|---------|-------|----------|
| **API URL** | http://localhost:8080 | Any valid URL | client.properties |
| **Window Size** | 1200x800 | Min 800x600 | config.properties |
| **Max Dialog Count** | 5 | 1-10 | config.properties |
| **Log Paths** | /tmp/jtypo-*.log | Any writable path | config.properties |

### 11.2 System Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| **Domain Timeout** | 5000ms | Time before marking domain as timeout |
| **Batch Size** | 50 | Domains processed per batch |
| **Batch Delay** | 200ms | Delay between batches |
| **Max Parallel Checks** | 10 | Concurrent domain checks (virtual threads) |

### 11.3 Internationalization

| Locale | File | Status |
|--------|------|--------|
| **English (default)** | messages.properties | Complete |
| **French** | messages_fr.properties | Complete |

---

## 12. Appendices

### 12.1 Function Index

| ID | Function Name | Category |
|----|---------------|----------|
| F-GEN-001 | generateDashVariants | Generation |
| F-GEN-002 | generateHomoglyphVariants | Generation |
| F-GEN-003 | generateMisspellVariants | Generation |
| F-GEN-004 | generateTLDVariants | Generation |
| F-CHK-001 | checkHttpStatus | Checking |
| F-CHK-002 | captureScreenshot | Checking |
| F-CHK-003 | detectLanguage | Checking |
| F-DATA-001 | saveDomainResult | Data |
| F-DATA-002 | getCachedDomain | Data |
| F-DATA-003 | clearAllData | Data |
| F-EXP-001 | copyToClipboard | Export |
| F-EXT-001 | httpGet | External |
| F-EXT-002 | renderPage | External |
| F-EXT-003 | resolveDomain | External |

### 12.2 Use Case Index

| ID | Use Case Name | Primary Actor |
|----|---------------|---------------|
| UC-001 | Generate and Check Domains | Security Analyst |
| UC-002 | View Domain Details | Security Analyst |
| UC-003 | Export Results | Security Analyst |
| UC-004 | Clear All Data | Security Analyst |

### 12.3 Related Documents

- [Software Architecture](ARCHITECTURE.md)
- [API Documentation](API.md)
- [Deployment Guide](DEPLOYMENT.md)
- [Technical Reference](TECHNICAL_REFERENCE.md)

---

*End of Document*
