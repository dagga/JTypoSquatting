# JTypoSquatting - Technical Reference

**Version:** 2.0-alpha1  
**Last Updated:** March 2025

---

## Table of Contents

1. [Code Organization](#1-code-organization)
2. [Domain Generation Algorithms](#2-domain-generation-algorithms)
3. [Homoglyph Mappings](#3-homoglyph-mappings)
4. [Misspell Patterns](#4-misspell-patterns)
5. [TLD Lists](#5-tld-lists)
6. [Class Reference](#6-class-reference)
7. [Database Schema](#7-database-schema)
8. [Configuration Reference](#8-configuration-reference)
9. [Performance Tuning](#9-performance-tuning)
10. [Development Guidelines](#10-development-guidelines)

---

## 1. Code Organization

### 1.1 Project Structure

```
JTypoSquatting/
├── backend/
│   └── src/main/java/com/aleph/graymatter/jtyposquatting/
│       ├── controller/           # REST controllers
│       │   ├── TypoSquattingController.java
│       │   └── DomainDataController.java
│       ├── service/              # Business logic
│       │   ├── DomainCheckService.java
│       │   └── PageAnalyzer.java
│       ├── db/                   # Database access
│       │   └── DatabaseService.java
│       └── JTypoSquattingApplication.java
│
├── frontend/
│   └── src/main/java/com/aleph/graymatter/jtyposquatting/
│       ├── ui/                   # Swing components
│       │   ├── JTypoFrame.java
│       │   ├── DomainStreamingService.java
│       │   ├── PreviewPanel.java
│       │   ├── LogPanel.java
│       │   ├── DomainDetailsDialog.java
│       │   └── renderers/
│       ├── client/               # REST client
│       │   └── JTypoSquattingRestClient.java
│       ├── config/               # Configuration
│       │   ├── ClientConfig.java
│       │   └── ConfigManager.java
│       └── Main.java
│
├── shared/
│   └── src/main/java/com/aleph/graymatter/jtyposquatting/
│       ├── JTypoSquatting.java   # Domain generation
│       └── dto/                  # Data Transfer Objects
│           ├── DomainResultDTO.java
│           └── DomainPageDTO.java
│
├── doc/                          # Documentation
├── scripts/                      # Utility scripts
└── build.gradle                  # Build configuration
```

### 1.2 Module Dependencies

```
┌──────────────┐
│   Frontend   │
└──────┬───────┘
       │ depends on
       ▼
┌──────────────┐
│    Shared    │◄──────┐
└──────────────┘       │
       ▲               │ depends on
       │               ▼
       └─────────┌──────────────┐
                 │   Backend    │
                 └──────────────┘
```

---

## 2. Domain Generation Algorithms

### 2.1 Dash Operations

**File:** `shared/src/main/java/com/aleph/graymatter/jtyposquatting/JTypoSquatting.java`

```java
// Add dash between each character pair
"example" → "e-xample", "ex-ample", "exa-mple", ...

// Remove existing dashes
"ex-ample" → "example"

// Move dash positions
"ex-ample" → "e-xample", "exa-mple", ...
```

**Implementation:**

```java
private void addDashVariants(String domain, List<String> variants) {
    String name = extractDomainName(domain);
    for (int i = 1; i < name.length(); i++) {
        String variant = name.substring(0, i) + "-" + name.substring(i);
        variants.add(variant + getTLD(domain));
    }
}
```

### 2.2 Homoglyph Substitution

**Algorithm:**

```
For each character in domain:
  For each homoglyph of character:
    Create variant with substitution
    Recursively process remaining characters
```

**Complexity:** O(n × m) where n = domain length, m = avg homoglyphs per char

### 2.3 Misspell Generation

**Patterns Applied:**

| Pattern | Example | Count |
|---------|---------|-------|
| Double consonant | `absence` → `abbence` | ~50 |
| Swap adjacent | `absence` → `absecne` | ~30 |
| Silent letter drop | `absence` → `absnce` | ~20 |
| Phonetic substitution | `absence` → `absense` | ~40 |

### 2.4 TLD Variations

**Operations:**

1. **Replace TLD:** `example.eu` → `example.com`, `example.net`, ...
2. **Add TLD suffix:** `example.eu` → `example.eu.com`
3. **Country TLD:** Add common country TLDs (.fr, .de, .co.uk)

---

## 3. Homoglyph Mappings

### 3.1 Latin Character Homoglyphs

| Character | Homoglyphs | Unicode |
|-----------|------------|---------|
| `a` | а (Cyrillic), ɑ (Latin alpha), Ꭺ (Cherokee) | U+0430, U+0251, U+13AA |
| `b` | Ь (Cyrillic), Ƅ (Cyrillic) | U+042C, U+0484 |
| `c` | с (Cyrillic), ϲ (Greek lunate) | U+0441, U+03F2 |
| `e` | е (Cyrillic), ɛ (Latin epsilon), ἐ (Greek) | U+0435, U+025B, U+1F10 |
| `i` | і (Cyrillic), ɩ (Latin iota), ι (Greek) | U+0456, U+0269, U+03B9 |
| `l` | 1 (digit), I (uppercase i), | (pipe) | U+0031, U+0049, U+007C |
| `o` | 0 (digit), ο (Greek omicron), о (Cyrillic) | U+0030, U+03BF, U+043E |
| `p` | р (Cyrillic), ρ (Greek rho) | U+0440, U+03C1 |
| `s` | ѕ (Cyrillic), ʂ (Latin hook) | U+0455, U+0282 |
| `u` | ս (Armenian), υ (Greek upsilon) | U+057D, U+03C5 |
| `x` | х (Cyrillic), χ (Greek chi) | U+0445, U+03C7 |
| `y` | у (Cyrillic), γ (Greek gamma) | U+0443, U+03B3 |

### 3.2 Complete Mapping Table

```java
// Partial mapping from JTypoSquatting.java
private static final Map<Character, List<Character>> HOMOGLYPHS = Map.of(
    'a', List.of('\u0430', '\u0251', '\u13AA'),      // а, ɑ, Ꭺ
    'c', List.of('\u0441', '\u03F2'),                // с, ϲ
    'e', List.of('\u0435', '\u025B'),                // е, ɛ
    'i', List.of('\u0456', '\u0269', '\u03B9'),      // і, ɩ, ι
    'o', List.of('\u043E', '\u03BF', '\u0030'),      // о, ο, 0
    'p', List.of('\u0440', '\u03C1'),                // р, ρ
    's', List.of('\u0455', '\u0282'),                // ѕ, ʂ
    'x', List.of('\u0445', '\u03C7'),                // х, χ
    'y', List.of('\u0443', '\u03B3'),                // у, γ
    // ... full mapping in source code
);
```

### 3.3 Visual Similarity Matrix

```
ASCII    Cyrillic   Greek    Visual Similarity
------   --------   -----    -----------------
a        а          α        High (identical in many fonts)
c        с          ϲ        High
e        е          ε        High
i        і          ι        High
o        о          ο        High
p        р          ρ        High
x        х          χ        High
y        у          γ        Medium
```

---

## 4. Misspell Patterns

### 4.1 Common English Misspellings

```java
private static final Map<String, List<String>> MISSPELLINGS = Map.of(
    "absence",    List.of("absense", "absences"),
    "accommodate",List.of("acommodate", "accomodate"),
    "accident",   List.of("acident", "accidently"),
    "achieve",    List.of("acheive", "acheive"),
    "acquire",    List.of("aquire", "acquistion"),
    "address",    List.of("adress", "addres"),
    "advice",     List.of("advise", "advisory"),
    "affiliate",  List.of("afiliate", "affilliate"),
    "against",    List.of("againts", "agnist"),
    "algorithm",  List.of("algoritm", "algorithim"),
    // ... 500+ common misspellings
);
```

### 4.2 Pattern-Based Transformations

| Pattern | Description | Example |
|---------|-------------|---------|
| **Double Letter** | Add extra consonant | `stop` → `stopp` |
| **Swap Vowels** | Swap adjacent vowels | `ae` → `ea` |
| **Silent H** | Drop or add H | `hour` → `our` |
| **IE/EI** | Common confusion | `receive` → `recieve` |
| **ABLE/IBLE** | Suffix confusion | `possible` → `possable` |

---

## 5. TLD Lists

### 5.1 Common TLDs

```java
private static final List<String> COMMON_TLDS = List.of(
    ".com", ".net", ".org", ".info", ".biz",
    ".eu", ".fr", ".de", ".es", ".it", ".nl",
    ".co.uk", ".co.jp", ".com.au", ".com.br",
    ".io", ".ai", ".app", ".dev", ".tech",
    ".online", ".site", ".store", ".shop",
    // ... 100+ common TLDs
);
```

### 5.2 Country Code TLDs (ccTLD)

| Code | Country | Usage |
|------|---------|-------|
| `.us` | United States | Common |
| `.uk` | United Kingdom | Common |
| `.de` | Germany | Common |
| `.fr` | France | Common |
| `.cn` | China | Common |
| `.ru` | Russia | Common |
| `.br` | Brazil | Common |
| `.in` | India | Common |

### 5.3 Typosquatting-Prone TLDs

These TLDs are commonly used for typosquatting:

| TLD | Reason |
|-----|--------|
| `.cm` | Typo of `.com` |
| `.con` | Typo of `.com` |
| `.comm` | Typo of `.com` |
| `.om` | Typo of `.com` |
| `.co` | Typo of `.com` |
| `.omg` | Visual similarity |

---

## 6. Class Reference

### 6.1 Core Classes

#### JTypoSquatting (Shared)

**Purpose:** Domain generation algorithms

**Methods:**
```java
public JTypoSquatting(String domain) throws InvalidDomainException
public ArrayList<String> getListOfDomains()
private void addDashVariants(String domain, List<String> list)
private void addHomoglyphVariants(String domain, List<String> list)
private void addMisspellVariants(String domain, List<String> list)
private void addTLDVariants(String domain, List<String> list)
```

#### DomainCheckService (Backend)

**Purpose:** Check domain availability

**Methods:**
```java
public DomainResultDTO checkDomain(String domain)
private int checkHttpStatus(String url)
private String fetchTitle(String url)
private String detectLanguage(String content)
```

#### PageAnalyzer (Backend)

**Purpose:** Full page analysis with screenshot

**Methods:**
```java
public DomainPageDTO analyzePage(String domain)
private byte[] captureScreenshot(URL url)
private String extractMetadata(Document doc)
```

#### JTypoFrame (Frontend)

**Purpose:** Main application window

**Fields:**
```java
private final JTable jTableOutput
private final DefaultTableModel tableModel
private final JTextField jTextFieldConsole
private final PreviewPanel previewPanel
private final ConfigManager config
```

**Methods:**
```java
private void generateAndCheckDomains()
private void processDomainUpdate(DomainResultDTO result)
private void updateConsole()
private void showDomainDetails()
```

#### DomainStreamingService (Frontend)

**Purpose:** Handle SSE stream from backend

**Methods:**
```java
public void startDomainChecks(String domain)
public void cancelActiveStreamIfAny()
private void scheduleTimeoutCheck(String domain)
public int getActiveCount()
public int getDeadCount()
```

### 6.2 DTOs

#### DomainResultDTO

```java
public class DomainResultDTO {
    private String domain;
    private String status;
    private String title;
    private String language;
    private String description;
    private int httpCode;
    private String screenshotBase64;  // Serialized as Base64
    private transient byte[] screenshot;  // Deserialized
    private String homepageText;
    private Map<String, String> httpHeaders;
}
```

#### DomainPageDTO

```java
public class DomainPageDTO extends DomainResultDTO {
    private String metaDescription;
    private String metaKeywords;
    private String metaAuthor;
    private String metaOgTitle;
    private String metaOgDescription;
    private String detectedLanguage;
    private Timestamp timestamp;
}
```

---

## 7. Database Schema

### 7.1 Table Definition

```sql
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
    meta_og_title TEXT,
    meta_og_description TEXT,
    detected_language VARCHAR(50),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_domain ON domain_results(domain);
CREATE INDEX idx_status ON domain_results(status);
CREATE INDEX idx_timestamp ON domain_results(timestamp);
```

### 7.2 Database Configuration

**H2 Connection URL:**
```
jdbc:h2:~/jtyposquatting/db;AUTO_SERVER=TRUE
```

**Settings:**
- Auto-commit: enabled
- Transaction isolation: READ_COMMITTED
- Max connections: 16

---

## 8. Configuration Reference

### 8.1 All Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `api.default.url` | `http://localhost:8080` | Backend API URL |
| `api.timeout.ms` | `10000` | API connection timeout |
| `domain.testing.timeout.ms` | `5000` | Domain check timeout |
| `domain.batch.size` | `50` | Domains per batch |
| `domain.batch.delay.ms` | `200` | Delay between batches |
| `domain.max.parallel.checks` | `10` | Max concurrent checks |
| `ui.window.width` | `1200` | Window width (pixels) |
| `ui.window.height` | `800` | Window height (pixels) |
| `ui.table.row.height` | `24` | Table row height |
| `ui.preview.width` | `320` | Screenshot width |
| `ui.preview.height` | `240` | Screenshot height |
| `ui.max.open.dialogs` | `5` | Max detail dialogs |
| `log.backend.path` | `/tmp/jtypo-backend.log` | Backend log path |
| `log.frontend.path` | `/tmp/jtypo-frontend.log` | Frontend log path |

### 8.2 Spring Boot Properties

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port |
| `spring.threads.virtual.enabled` | `true` | Enable virtual threads |
| `spring.task.execution.pool.core-size` | `16` | Core thread pool size |
| `spring.task.execution.pool.max-size` | `128` | Max thread pool size |
| `server.jetty.threads.max` | `200` | Max Jetty threads |
| `spring.mvc.async.request-timeout` | `300000` | SSE timeout (5 min) |

---

## 9. Performance Tuning

### 9.1 JVM Options

```bash
# Production settings
java -Xms512M -Xmx2G \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=100 \
     -Djava.awt.headless=false \
     -jar JTypoSquatting.jar
```

### 9.2 Virtual Thread Tuning

| Parameter | Default | Recommended |
|-----------|---------|-------------|
| Core pool size | 16 | CPU cores × 2 |
| Max pool size | 128 | 256-512 for high concurrency |
| Keep-alive | 60s | 30s |

### 9.3 Memory Optimization

**Screenshot Memory:**
- Each screenshot: ~50-200 KB
- For 1000 domains: ~50-200 MB
- Recommendation: Limit concurrent screenshots

**Database Memory:**
- H2 cache: 16 MB default
- For large datasets: Increase to 64 MB
- Setting: `CACHE_SIZE=65536`

### 9.4 Network Tuning

```properties
# Connection pooling
http.connectTimeout=10000
http.readTimeout=30000
http.maxConnections=100

# DNS caching
networkaddress.cache.ttl=300
networkaddress.cache.negative.ttl=60
```

---

## 10. Development Guidelines

### 10.1 Code Style

- **Java Version:** 21+
- **Indentation:** 4 spaces
- **Line Length:** 120 characters max
- **Naming:** CamelCase for classes, PascalCase for methods

### 10.2 Testing

**Run Tests:**
```bash
./gradlew test
```

**Test Coverage:**
```bash
./gradlew jacocoTestReport
```

### 10.3 Building

**Development Build:**
```bash
./gradlew clean build
```

**Production Build:**
```bash
./gradlew clean :frontend:fatJar
```

### 10.4 Debugging

**Remote Debug (Backend):**
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar backend.jar
```

**Remote Debug (Frontend):**
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 \
     -jar JTypoSquatting.jar
```

### 10.5 Logging

**Log Levels:**
- ERROR: Critical failures
- WARN: Recoverable issues
- INFO: Normal operation
- DEBUG: Detailed diagnostics
- TRACE: Full trace

**Enable Debug Logging:**
```properties
logging.level.com.aleph.graymatter=DEBUG
```

---

## Appendix A: HTTP Status Code Reference

| Code | Meaning | JTypoSquatting Status |
|------|---------|----------------------|
| 200 | OK | Suspicious |
| 301 | Moved Permanently | Safe |
| 302 | Found | Safe |
| 304 | Not Modified | Safe |
| 400 | Bad Request | Safe |
| 403 | Forbidden | Safe |
| 404 | Not Found | Safe |
| 500 | Internal Server Error | Safe |
| 0 | Connection Failed | Dead |
| -1 | In Progress | Testing... |

---

## Appendix B: Language Codes

| Code | Language | Code | Language |
|------|----------|------|----------|
| EN | English | DE | German |
| FR | French | ES | Spanish |
| IT | Italian | PT | Portuguese |
| NL | Dutch | RU | Russian |
| PL | Polish | TR | Turkish |
| ZH | Chinese | JA | Japanese |
| KO | Korean | AR | Arabic |
| HI | Hindi | TH | Thai |

---

## Appendix C: Related Documentation

- [Software Architecture](ARCHITECTURE.md)
- [Functional Architecture](FUNCTIONAL_ARCHITECTURE.md)
- [API Documentation](API.md)
- [Deployment Guide](DEPLOYMENT.md)

---

*End of Document*
