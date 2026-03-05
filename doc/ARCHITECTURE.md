# JTypoSquatting - Software Architecture Documentation

**Version:** 2.0-alpha1  
**Last Updated:** March 2025  
**Status:** Draft

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [System Overview](#2-system-overview)
3. [Architecture Patterns](#3-architecture-patterns)
4. [System Components](#4-system-components)
5. [Technology Stack](#5-technology-stack)
6. [Data Architecture](#6-data-architecture)
7. [Integration Architecture](#7-integration-architecture)
8. [Security Architecture](#8-security-architecture)
9. [Performance Architecture](#9-performance-architecture)
10. [Deployment Architecture](#10-deployment-architecture)
11. [Architecture Decisions](#11-architecture-decisions)
12. [Appendices](#12-appendices)

---

## 1. Introduction

### 1.1 Purpose

This document provides a comprehensive description of the software architecture for JTypoSquatting, a typo squatting domain detection tool. It serves as a reference for developers, architects, and stakeholders involved in the development, maintenance, and deployment of the system.

### 1.2 Scope

JTypoSquatting generates potential typo squatted domains from a given domain name and checks their status (active, dead, or unreachable). The system provides real-time feedback through a graphical user interface and stores results in a local database.

### 1.3 Definitions and Acronyms

| Term | Definition |
|------|------------|
| **Typo Squatting** | Practice of registering domain names similar to popular domains to exploit typing errors |
| **SSE** | Server-Sent Events - push technology for real-time server-to-client communication |
| **DTO** | Data Transfer Object - design pattern for data transfer between layers |
| **Virtual Threads** | Lightweight threads introduced in Java 21 (Project Loom) |
| **TLD** | Top-Level Domain (e.g., .com, .org, .eu) |

---

## 2. System Overview

### 2.1 System Context

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              JTypoSquatting System                          │
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                 │
│  │   Frontend   │◄──►│   Backend    │◄──►│   Database   │                 │
│  │   (Swing)    │    │ (Spring Boot)│    │    (H2)      │                 │
│  └──────────────┘    └──────────────┘    └──────────────┘                 │
│         │                   │                                              │
│         │                   │                                              │
│         ▼                   ▼                                              │
│  ┌──────────────┐    ┌──────────────┐                                     │
│  │    User      │    │  External    │                                     │
│  │  (Security   │    │   Websites   │                                     │
│  │   Analyst)   │    │  (HTTP/S)    │                                     │
│  └──────────────┘    └──────────────┘                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 System Goals

| Goal | Description | Priority |
|------|-------------|----------|
| **Real-time Processing** | Stream domain check results as they complete | High |
| **High Concurrency** | Check hundreds of domains simultaneously | High |
| **User Experience** | Intuitive GUI with live updates and previews | High |
| **Data Persistence** | Store results for later analysis | Medium |
| **Offline Capability** | Run locally without cloud dependencies | Medium |

---

## 3. Architecture Patterns

### 3.1 Client-Server Architecture

The system follows a **client-server architecture** with clear separation between:

- **Client (Frontend)**: Swing-based desktop application
- **Server (Backend)**: Spring Boot REST API with SSE support

### 3.2 Layered Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Presentation Layer                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │   JTypoFrame    │  │  PreviewPanel   │  │   LogPanel      │ │
│  │   (Main UI)     │  │  (Screenshot)   │  │  (Log Viewer)   │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                         Service Layer                           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │DomainStreaming  │  │  ConfigManager  │  │   RestClient    │ │
│  │    Service      │  │     (i18n)      │  │                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                          Data Layer                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  DomainResult   │  │  DomainPage     │  │  Configuration  │ │
│  │      DTO        │  │      DTO        │  │   Properties    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 Event-Driven Architecture (SSE)

Server-Sent Events enable real-time communication:

```
┌──────────────┐                              ┌──────────────┐
│   Backend    │                              │   Frontend   │
│              │                              │              │
│  Generate    │                              │              │
│  Domains     │                              │              │
│      │       │                              │              │
│      ▼       │                              │              │
│  Stream SSE  │────data: {...}──────────────►│  Update UI   │
│  Events      │────data: {...}──────────────►│  Update UI   │
│              │────data: {...}──────────────►│  Update UI   │
│              │                              │              │
└──────────────┘                              └──────────────┘
```

---

## 4. System Components

### 4.1 Component Diagram

```
                                    ┌─────────────────┐
                                    │   JTypoSquat    │
                                    │     (Main)      │
                                    └────────┬────────┘
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │                        │                        │
           ┌────────▼────────┐    ┌──────────▼─────────┐   ┌─────────▼────────┐
           │    Frontend     │    │      Backend       │   │      Shared      │
           │   (Swing UI)    │    │  (Spring Boot API) │   │  (Common Code)   │
           └────────┬────────┘    └──────────┬─────────┘   └─────────┬────────┘
                    │                        │                       │
     ┌──────────────┼──────────────┐        │        ┌──────────────┼──────────────┐
     │              │              │        │        │              │              │
┌────▼────┐  ┌─────▼─────┐  ┌────▼────┐   │   ┌────▼────┐  ┌──────▼──────┐  ┌────▼────┐
│  UI     │  │ Streaming │  │  REST   │   │   │Controller│  │   Service   │  │ Domain  │
│Components│  │  Service  │  │ Client  │   │   │          │  │   Layer     │  │  Gen    │
└─────────┘  └───────────┘  └─────────┘   │   └──────────┘  └─────────────┘  └─────────┘
                                           │
                                    ┌──────▼──────┐
                                    │  Database   │
                                    │    (H2)     │
                                    └─────────────┘
```

### 4.2 Module Descriptions

#### 4.2.1 Frontend Module

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| `JTypoFrame` | Main application window, table display | Java Swing |
| `DomainStreamingService` | SSE event handling, domain tracking | Java Concurrent |
| `PreviewPanel` | Screenshot display | Java Swing |
| `LogPanel` | Log file tailing and display | Java IO |
| `DomainDetailsDialog` | Domain detail view | Java Swing |
| `ConfigManager` | i18n and configuration management | Java ResourceBundle |

#### 4.2.2 Backend Module

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| `TypoSquattingController` | Domain generation/checking endpoint | Spring Boot + Virtual Threads |
| `DomainDataController` | Data management endpoints | Spring Boot |
| `DomainCheckService` | Domain availability checking | Java HTTP Client |
| `PageAnalyzer` | Web page analysis and screenshot | JavaFX WebView |
| `DatabaseService` | H2 database operations | JDBC |

#### 4.2.3 Shared Module

| Component | Responsibility | Technology |
|-----------|---------------|------------|
| `JTypoSquatting` | Domain generation algorithms | Java |
| `DomainResultDTO` | Data transfer object | Java |
| `DomainPageDTO` | Page analysis DTO | Java |
| `LanguageDetector` | Language detection | Lingua Library |

### 4.3 Domain Generation Algorithms

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Domain Generation Pipeline                       │
│                                                                     │
│  Input: www.example.com                                             │
│         │                                                           │
│         ▼                                                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐│
│  │    Dash     │  │  Homoglyph  │  │  Misspell   │  │    TLD     ││
│  │  Generator  │  │  Generator  │  │  Generator  │  │  Generator ││
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └─────┬──────┘│
│         │                │                │                │        │
│         └────────────────┴────────────────┴────────────────┘        │
│                              │                                      │
│                              ▼                                      │
│                    ┌─────────────────┐                              │
│                    │  Deduplicated   │                              │
│                    │  Domain List    │                              │
│                    └─────────────────┘                              │
└─────────────────────────────────────────────────────────────────────┘
```

#### Algorithm Details

| Algorithm | Description | Example |
|-----------|-------------|---------|
| **Dash** | Add, remove, or move hyphens | `example.com` → `ex-ample.com` |
| **Homoglyph** | Replace characters with visually similar ones | `example` → `ехаmple` (Cyrillic) |
| **Misspell** | Common spelling mistakes (English) | `absence` → `absense` |
| **TLD** | Change or add TLDs | `example.eu` → `example.fr`, `example.eu.com` |

---

## 5. Technology Stack

### 5.1 Runtime Environment

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| **JDK** | OpenJDK | 21+ | Virtual Threads support |
| **Build Tool** | Gradle | 9.0 | Dependency management, build automation |

### 5.2 Frontend Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java Swing** | Built-in | Desktop UI framework |
| **Gson** | 2.10.1 | JSON serialization/deserialization |
| **Guava** | 33.1.0-jre | Utility libraries |

### 5.3 Backend Technologies

| Technology | Version | Purpose |
|------------|---------|---------|
| **Spring Boot** | 3.5.0 | REST API framework |
| **Jetty** | Built-in | Embedded servlet container |
| **H2 Database** | 2.2.224 | Embedded database |
| **JSoup** | 1.17.2 | HTML parsing |
| **Lingua** | 1.2.1 | Language detection |

### 5.4 Infrastructure

| Component | Technology | Purpose |
|-----------|------------|---------|
| **CI/CD** | GitHub Actions | Automated builds and releases |
| **Package** | jpackage | Native installers (MSI, EXE, app-image) |

---

## 6. Data Architecture

### 6.1 Database Schema

```sql
-- Domain Results Table
CREATE TABLE domain_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50),
    title VARCHAR(500),
    language VARCHAR(50),
    description TEXT,
    http_code INTEGER,
    http_headers TEXT,
    screenshot BLOB,
    homepage_text TEXT,
    meta_description TEXT,
    meta_keywords TEXT,
    meta_author TEXT,
    detected_language VARCHAR(50),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 6.2 Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    DomainResults Table                      │
├─────────────────────────────────────────────────────────────┤
│  PK: id (BIGINT)                                            │
│  UK: domain (VARCHAR)                                       │
│                                                             │
│  Core Fields:                                               │
│  ├── status (VARCHAR) - Suspicious/Safe/Dead/Testing       │
│  ├── http_code (INTEGER) - HTTP response code              │
│  ├── http_headers (TEXT) - Response headers (JSON)         │
│  │                                                          │
│  Content Fields:                                            │
│  ├── title (VARCHAR) - Page title                          │
│  ├── description (TEXT) - Page description                 │
│  ├── language (VARCHAR) - Detected language                │
│  ├── screenshot (BLOB) - Page screenshot                   │
│  │                                                          │
│  Metadata:                                                  │
│  ├── meta_description (TEXT)                               │
│  ├── meta_keywords (TEXT)                                  │
│  ├── meta_author (TEXT)                                    │
│  └── timestamp (TIMESTAMP) - Analysis timestamp            │
└─────────────────────────────────────────────────────────────┘
```

### 6.3 Data Flow

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   User       │     │   Frontend   │     │   Backend    │
│   Input      │     │              │     │              │
└──────┬───────┘     └──────┬───────┘     └──────┬───────┘
       │                    │                    │
       │ Enter domain       │                    │
       │───────────────────►│                    │
       │                    │                    │
       │                    │ Generate & Check   │
       │                    │───────────────────►│
       │                    │                    │
       │                    │  SSE Stream        │
       │                    │◄───────────────────│
       │                    │  (Domain Results)  │
       │                    │                    │
       │                    │ Save to DB         │
       │                    │───────────────────►│
       │                    │                    │
       │                    │                    │ Query/Save
       │                    │                    │◄──────► DB
       │                    │                    │
       │ Display Results    │                    │
       │◄───────────────────│                    │
       │                    │                    │
```

---

## 7. Integration Architecture

### 7.1 Internal Integration

```
┌─────────────────────────────────────────────────────────────────┐
│                     Internal Component Flow                     │
└─────────────────────────────────────────────────────────────────┘

Frontend Components:
┌──────────────┐    HTTP    ┌──────────────┐
│  JTypoFrame  │───────────►│ RestClient   │
└──────────────┘            └──────────────┘
                                    │
                                    │ SSE Stream
                                    ▼
                            ┌──────────────┐
                            │  Streaming   │
                            │   Service    │
                            └──────────────┘

Backend Components:
┌──────────────┐    Virtual   ┌──────────────┐
│  Controller  │───Threads───►│   Service    │
└──────────────┘              └──────────────┘
                                      │
                                      ▼
                              ┌──────────────┐
                              │  PageAnalyzer│
                              └──────────────┘
                                      │
                                      ▼
                              ┌──────────────┐
                              │  Database    │
                              │   Service    │
                              └──────────────┘
```

### 7.2 External Integration

| External System | Protocol | Purpose |
|-----------------|----------|---------|
| **Target Websites** | HTTP/HTTPS | Domain availability checking, screenshot capture |
| **DNS Servers** | DNS | Domain resolution verification |

---

## 8. Security Architecture

### 8.1 Security Considerations

| Aspect | Implementation |
|--------|----------------|
| **Local Execution** | All processing runs locally, no data sent to external servers |
| **Database** | H2 embedded database with file-based storage |
| **Network** | Outbound connections only to target domains |
| **User Data** | No user authentication required (single-user tool) |

### 8.2 Security Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│                    Security Perimeter                       │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │              Trusted Zone (Local)                     │ │
│  │                                                       │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐           │ │
│  │  │ Frontend │  │ Backend  │  │ Database │           │ │
│  │  └──────────┘  └──────────┘  └──────────┘           │ │
│  │                                                       │ │
│  └───────────────────────────────────────────────────────┘ │
│                          │                                  │
│                          │ Outbound HTTP/S only            │
│                          ▼                                  │
│  ┌───────────────────────────────────────────────────────┐ │
│  │            Untrusted Zone (Internet)                  │ │
│  │                                                       │ │
│  │         Target domains (potential typos)              │ │
│  │                                                       │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 9. Performance Architecture

### 9.1 Virtual Threads Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│              Virtual Thread Pool (Java 21+)                     │
│                                                                 │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐     ┌─────────┐        │
│  │ Thread  │  │ Thread  │  │ Thread  │ ... │ Thread  │        │
│  │   #1    │  │   #2    │  │   #3    │     │   #N    │        │
│  │ (1 KB)  │  │ (1 KB)  │  │ (1 KB)  │     │ (1 KB)  │        │
│  └────┬────┘  └────┬────┘  └────┬────┘     └────┬────┘        │
│       │           │           │                │              │
│       └───────────┴───────────┴────────────────┘              │
│                           │                                    │
│                           ▼                                    │
│              ┌─────────────────────────┐                      │
│              │   Domain Check Tasks    │                      │
│              │  (HTTP + Screenshot)    │                      │
│              └─────────────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘

Benefits:
- Memory: ~1 KB per thread vs ~1 MB for platform threads
- Concurrency: 1000+ simultaneous domain checks
- No thread pool tuning required
```

### 9.2 Performance Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| **Concurrent Checks** | 100-1000 domains | Virtual threads |
| **Memory per Thread** | ~1 KB | JVM monitoring |
| **Response Time** | < 5 seconds per domain | SSE stream |
| **Screenshot Capture** | < 3 seconds | JavaFX WebView |

### 9.3 Bottleneck Analysis

| Component | Bottleneck | Mitigation |
|-----------|------------|------------|
| **Network** | DNS resolution, HTTP latency | Parallel checks with virtual threads |
| **Screenshot** | JavaFX WebView initialization | Reuse WebView instances |
| **Memory** | Screenshot storage (BLOB) | Limit screenshot size, compression |
| **Database** | Concurrent writes | H2 transaction management |

---

## 10. Deployment Architecture

### 10.1 Deployment Options

```
┌─────────────────────────────────────────────────────────────────┐
│                    Deployment Architectures                     │
└─────────────────────────────────────────────────────────────────┘

Option 1: Standalone JAR
┌─────────────────────────────────────────┐
│  java -jar JTypoSquatting.jar           │
│                                         │
│  ├── Frontend (embedded)                │
│  ├── Backend (embedded)                 │
│  └── Database (file-based H2)           │
└─────────────────────────────────────────┘

Option 2: Native Installer (jpackage)
┌─────────────────────────────────────────┐
│  JTypoSquatting.exe / .app / .deb       │
│                                         │
│  ├── Bundled JRE                        │
│  ├── Application JAR                    │
│  └── Native launcher                    │
└─────────────────────────────────────────┘
```

### 10.2 System Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **Java Version** | JDK 21 | JDK 21+ |
| **RAM** | 2 GB | 4 GB+ |
| **Disk Space** | 500 MB | 1 GB+ |
| **Display** | 1280x720 | 1920x1080+ |
| **Network** | Internet access | Broadband |

---

## 11. Architecture Decisions

### 11.1 Key Decisions Log

| ID | Decision | Rationale | Consequences |
|----|----------|-----------|--------------|
| **AD-001** | Use Java 21 with Virtual Threads | High concurrency for I/O bound domain checks | Requires JDK 21+, simpler code |
| **AD-002** | Client-Server with SSE | Real-time updates, clean separation | Two processes to manage |
| **AD-003** | H2 Embedded Database | No external database setup, portable | Limited to single-user |
| **AD-004** | JavaFX for Screenshots | Full browser rendering (JavaScript support) | JavaFX runtime required |
| **AD-005** | Fat JAR distribution | Single file deployment, no installation | Larger file size (32 MB) |
| **AD-006** | Swing for UI | Mature, stable, no additional dependencies | Less modern look than JavaFX |

### 11.2 Trade-off Analysis

#### Virtual Threads vs Thread Pools

| Aspect | Virtual Threads | Thread Pools |
|--------|-----------------|--------------|
| **Memory** | ~1 KB/thread | ~1 MB/thread |
| **Max Concurrency** | 1000+ | ~100-200 |
| **Configuration** | None required | Pool sizing needed |
| **Java Version** | Java 21+ | Java 8+ |

#### Swing vs JavaFX for UI

| Aspect | Swing | JavaFX |
|--------|-------|--------|
| **Dependencies** | Built-in | External (already used for screenshots) |
| **Look & Feel** | Native OS | Customizable |
| **Learning Curve** | Low | Medium |
| **Decision** | ✅ Selected | Not selected for UI |

---

## 12. Appendices

### 12.1 File Structure

```
JTypoSquatting/
├── backend/
│   ├── src/main/java/.../controller/    # REST controllers
│   ├── src/main/java/.../service/       # Business logic
│   ├── src/main/java/.../db/            # Database access
│   └── src/main/resources/
│       └── application.properties       # Spring config
├── frontend/
│   ├── src/main/java/.../ui/            # Swing components
│   ├── src/main/java/.../client/        # REST client
│   ├── src/main/java/.../config/        # Configuration
│   └── src/main/resources/
│       ├── messages.properties          # i18n (English)
│       ├── messages_fr.properties       # i18n (French)
│       └── config.properties            # App config
├── shared/
│   └── src/main/java/.../dto/           # Data Transfer Objects
├── doc/                                 # Documentation
├── .github/workflows/                   # CI/CD
└── build.gradle                         # Build configuration
```

### 12.2 Reference Documents

- [Functional Architecture](FUNCTIONAL_ARCHITECTURE.md)
- [API Documentation](API.md)
- [Deployment Guide](DEPLOYMENT.md)
- [Technical Reference](TECHNICAL_REFERENCE.md)

### 12.3 Revision History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 2.0-alpha1 | March 2025 | Development Team | Initial architecture documentation |

---

*End of Document*
