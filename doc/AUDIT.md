# In-Depth Code Audit Report — JTypoSquatting

**Project:** JTypoSquatting  
**Version audited:** 2.0-alpha1  
**Audit date:** September 2026  
**Scope:** `backend`, `frontend`, `shared` modules, Gradle configuration and run scripts  
**Status:** Improvement recommendations and remediation plan; resolution status for version 2.0 below  

> **Historical document.** This audit covers the original desktop application (2.0-alpha1). The findings
> below are kept as written; their resolution in version 2.0 is summarised in this table. See also the
> progress in [ACTION_PLAN.md](ACTION_PLAN.md) (§ 4 and § 4bis).

### Resolution status (version 2.0, 2026-10-15)

| Finding | Status | Resolution |
|---|---|---|
| SEC-01 TLS verification disabled | ✅ Fixed | Permissive `TrustManager` and `HostnameVerifier` removed; an invalid certificate is reported as a `TlsStatus` signal (P1.4) |
| SEC-02 Malicious domains opened in the browser | ✅ Fixed | Confirmation dialog before opening a site, in the web frontend (P3.5) and in the Swing client; screenshot offered instead |
| SEC-03 Destructive endpoints without access control | ✅ Fixed | OpenID Connect login on the web; the legacy endpoints exist only in local mode, on 127.0.0.1 (P1.1, P2.3) |
| SEC-04 Wildcard CORS | ✅ Fixed | CORS removed: frontend and API share the same origin (P1.6) |
| CONC-01 Single shared JDBC connection | ✅ Fixed | `JdbcTemplate` on a HikariCP pool |
| CONC-02 Unsynchronised `SseEmitter` | ✅ Fixed | Synchronised sends; scan streams are written by a single thread (P2.3) |
| CONC-03 Unsynchronised collections in the Swing client | ✅ Fixed | `ConcurrentHashMap` in `DomainStreamingService` |
| CONC-04 Race on `TLD_LIST` | ✅ Fixed | Synchronised snapshot and reload |
| CONC-05 JavaFX thread blocked by `sleep()` | ✅ Fixed | JavaFX replaced by headless Chromium (P1.3) |
| BUG-01 `LogTailer` closed at startup | ✅ Fixed | Executor no longer closed by try-with-resources |
| BUG-02 Inconsistent status labels, counters at zero | ✅ Fixed | `DomainStatus` enum |
| BUG-03 Lost spelling variants | ✅ Fixed | `KeysValuesSwapMulti` keeps every variant |
| BUG-04 Domain names starting with a dot | ✅ Fixed | `Dash` rewritten; generated names validated |
| BUG-05 Faulty validation in `DomainName` | ✅ Fixed | Validation through Guava `InternetDomainName` |
| BUG-06 `ConfigManager.setLocale()` without effect | ✅ Fixed | The resource bundle is reloaded for the new locale |
| BUG-07 `NullPointerException` in `ClientConfig` | ✅ Fixed | Missing `client.properties` handled, default URL used |
| DEBT-01 17 announced algorithms not implemented | ✅ Fixed | The 21 declared algorithms are implemented (P0.2); new ones planned (P0.3) |
| DEBT-02 Dead code, unused DTOs | ✅ Fixed | `GenerationRequestDTO` is the body of `POST /api/scans` |
| DEBT-03 Redundant JSON libraries | 🟡 Partial | `json-simple` removed; Gson (generators, repositories) and Jackson (Spring) still coexist |
| DEBT-04 Phantom Tomcat parameters | ✅ Fixed | Replaced by their Jetty equivalents (P0.1) |
| DEBT-05 `System.out` / `System.err` logging | 🟡 Partial | SLF4J everywhere in the backend and shared code; one `System.err` left in the Swing `PreviewPanel` |
| TEST-01 No tests in `frontend` and `shared` | ✅ Fixed | Tests added for both modules |
| TEST-02 Unit tests depending on the network | ✅ Fixed | WireMock, S3Mock and Testcontainers; network tests opt-in (`-Dinclude.network=true`) (P2.7) |

---

## 1. Executive Summary and Dashboard

The in-depth audit of the JTypoSquatting code base shows a project built on modern technical foundations (Java 21, Spring Boot 3.5, Virtual Threads, reactive Swing interface with SSE), but suffering from critical structural defects in **concurrency**, **security**, **operational reliability** and **test quality**.

Several components have silent blocking bugs (e.g. the `LogTailer` log reader stopped immediately at startup, status counters stuck at zero, 90% of the spelling-mistake variants lost, and no thread safety on the JDBC connection, which can corrupt the database).

### Overall Rating Grid

| Dimension | Score | Status | Summary |
|---|:---:|:---:|---|
| **Overall architecture** | `13/20` | ⚠️ To consolidate | Good modular separation in theory, but hidden couplings and inconsistent startup. |
| **Application security** | `08/20` | 🔴 Critical | Full SSL bypass (TrustAll), direct navigation to malicious domains, permissive CORS. |
| **Concurrency & threads** | `07/20` | 🔴 Critical | Single JDBC connection shared by Virtual Threads, unsynchronised SseEmitter, 8 s JavaFX freeze. |
| **Reliability & robustness** | `10/20` | 🟠 Insufficient | Silent bugs in `LogTailer`, `KeysValuesSwap`, parsing of invalid domains with dashes. |
| **Maintainability & code** | `11/20` | 🟡 Average | Dead code (17 phantom algorithms, unused DTO), json-simple/gson mix, `System.out`. |
| **Test quality** | `06/20` | 🔴 Critical | No frontend/shared tests, fake backend unit tests depending on the network and X11. |

---

## 2. Critical Security Vulnerabilities

### SEC-01: Global, blind disabling of TLS/SSL verification (TrustAll)
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/service/PageAnalyzer.java` (lines 136-157)
- **Severity:** 🔴 **High** (CWE-295: Improper Certificate Validation)
- **Finding:**
  To query target domains over HTTPS, `PageAnalyzer` overrides the SSL trust manager and the hostname verifier with permissive implementations that accept any invalid, expired or self-signed certificate:
  ```java
  httpsConn.setHostnameVerifier((hostname, session) -> true);
  SSLContext sc = SSLContext.getInstance("TLS");
  sc.init(null, new TrustManager[]{
      new X509TrustManager() {
          public X509Certificate[] getAcceptedIssuers() { return null; }
          public void checkClientTrusted(X509Certificate[] certs, String authType) {}
          public void checkServerTrusted(X509Certificate[] certs, String authType) {}
      }
  }, new SecureRandom());
  httpsConn.setSSLSocketFactory(sc.getSocketFactory());
  ```
- **Impact:** Although the intent is to analyse squatting domains that may have misconfigured certificates, this approach disables cryptographic security at the connection level and makes the tool vulnerable to interception (Man-in-the-Middle) attacks. It also creates a `SecureRandom` on every request, which is suboptimal.
- **Remediation:** Wrap the SSL inspection in a dedicated HTTP client that records the certificate status (valid / self-signed / invalid) without hiding the information, or configure a controlled `SSLContext` without removing the global verification.

---

### SEC-02: Unverified malicious domains opened directly in the host browser
- **Location:** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/ui/DomainDetailsDialog.java` (lines 67-74)
- **Severity:** 🔴 **High** (CWE-601: URL Redirection to Untrusted Site)
- **Finding:**
  In the details dialog, the "Open in Browser" button runs directly:
  ```java
  openBtn.addActionListener(e -> {
      try {
          String urlStr = data.getDomain().startsWith("http") ? data.getDomain() : "https://" + data.getDomain();
          if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(urlStr));
      } catch (Exception ex) { ... }
  });
  ```
- **Impact:** The very purpose of JTypoSquatting is to detect phishing, scam or malware-infected domains. An analyst clicking this button opens the fraudulent site in their default personal browsing session, exposing their workstation to browser attacks (drive-by download, session theft, zero-day exploitation).
- **Remediation:** Add an explicit warning (confirmation dialog stating the risk of malicious exposure) before opening, or recommend opening the site in a sandbox (isolated / containerised browser).

---

### SEC-03: Destructive endpoints open without any access control
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/controller/TypoSquattingController.java` (lines 161-183) and `DomainDataController.java` (lines 138-146)
- **Severity:** 🟠 **Medium** (CWE-306: Missing Authentication for Critical Function)
- **Finding:**
  The `DELETE /api/cancel-and-clear` and `DELETE /api/data/all` endpoints immediately delete all the data of the H2 database, without any token or session validation.
- **Impact:** Any process or script running on the local network, or a browser sending a forged cross-origin request, can instantly purge the results of ongoing analyses.

---

### SEC-04: Permissive wildcard CORS configuration
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/config/WebConfig.java` (lines 17-23)
- **Severity:** 🟡 **Low to Medium**
- **Finding:**
  `registry.addMapping("/api/**").allowedOrigins("*")` lets any external web page query the local API on port 8080.
- **Remediation:** Restrict to the legitimate origins or to local requests `http://localhost:*`, `http://127.0.0.1:*`.

---

## 3. Major Concurrency & Thread-Safety Defects

### CONC-01: A single `java.sql.Connection` instance shared between Virtual Threads
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/db/DatabaseService.java` (lines 18, 33, 76-98)
- **Severity:** 🔴 **Critical** (CWE-388 / Concurrency Issue)
- **Finding:**
  The `@Component DatabaseService` class opens **one single JDBC connection** in its `@PostConstruct`:
  ```java
  private Connection connection;
  ...
  connection = DriverManager.getConnection(datasourceUrl, "sa", "");
  ```
  This same `connection` reference is then used concurrently in `save()` by dozens of Virtual Threads running `checkDomain()` in parallel:
  ```java
  try (PreparedStatement ps = connection.prepareStatement(sql)) { ... ps.executeUpdate(); }
  ```
- **Impact:** The JDBC specification explicitly states that `java.sql.Connection` **is not thread-safe**. Several threads running queries on the same connection at the same time interleave, disturb the internal transaction states, raise `SQLException: object closed` errors or corrupt the H2 data.
- **Remediation:** Immediately replace `DriverManager.getConnection()` with a connection pool managed by Spring Boot (`HikariCP` with `spring-boot-starter-jdbc`, or `JdbcTemplate`). Each query must borrow and return its own connection from the pool.

---

### CONC-02: Unsynchronised `SseEmitter` called concurrently by Virtual Threads
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/controller/TypoSquattingController.java` (lines 91-105, 185-194)
- **Severity:** 🔴 **Critical**
- **Finding:**
  In `generateAndCheckDomains()`, a loop submits each domain check to the virtual executor:
  ```java
  sessionExecutor.submit(() -> {
      DomainResultDTO finalResult = domainCheckService.checkDomain(generatedDomain);
      if (finalResult != null) {
          sendSseEvent(emitter, finalResult); // Not synchronised!
      }
  });
  ```
  In Spring MVC, however, `SseEmitter.send()` is **not thread-safe**.
- **Impact:** Two Virtual Threads finishing their analysis at the same moment call `emitter.send()` simultaneously. This raises `IllegalStateException: ResponseBodyEmitter has already been marked as complete` exceptions or produces an interleaved, corrupted SSE stream, causing JSON parsing errors in the frontend.
- **Remediation:** Synchronise the SSE sends on a dedicated lock, or use a dedicated event queue for the emitter:
  ```java
  private void sendSseEvent(SseEmitter emitter, Object data) throws IOException {
      synchronized (emitter) {
          emitter.send(SseEmitter.event()
              .name("domainUpdate")
              .data(data, MediaType.APPLICATION_JSON)
              .id(String.valueOf(System.currentTimeMillis())));
      }
  }
  ```

---

### CONC-03: Unsynchronised collections shared between the EDT and background threads
- **Location:** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/ui/DomainStreamingService.java` (lines 28-29, 38, 88-124, 155-172)
- **Severity:** 🟠 **High**
- **Finding:**
  The following fields are declared as plain `HashMap`s:
  ```java
  private final Map<String, Integer> domainRowMap = new HashMap<>();
  private final Map<String, Long> testingDomainTimestamps = new HashMap<>();
  private final Map<String, Boolean> processingState = new HashMap<>();
  ```
  However:
  - `drain` modifies them on the Swing Event Dispatch Thread (EDT) (`SwingUtilities.invokeLater`).
  - `scheduleTimeoutCheck` reads and modifies them from the `timeoutExecutor` pool (`TimeoutChecker` thread).
  - `startDomainChecks` and `reset` clear them from `executorService`.
- **Impact:** Concurrent access without synchronisation on a standard `HashMap`, with a risk of corrupted internal pointers (infinite loop in `HashMap.get()` or dirty reads).
- **Remediation:** Replace these structures with `ConcurrentHashMap`, or strictly synchronise every access.

---

### CONC-04: Critical race condition and untimely reset of `TLD_LIST`
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/generator/TLD.java` (lines 18, 87-90) and `JTypoSquatting.java` (lines 31-35)
- **Severity:** 🟠 **High**
- **Finding:**
  The `TLD_LIST` variable is a static, non-thread-safe `HashSet`.
  Each time `new JTypoSquatting(domain)` is instantiated, the constructor calls:
  ```java
  TLD.UpdateTLDList();
  ```
  Which does:
  ```java
  public static void UpdateTLDList() throws IOException {
      TLD_LIST.clear();
      loadTLDList();
  }
  ```
- **Impact:** If two analyses run at the same time, or if a request arrives while another one is finishing, `TLD_LIST.clear()` empties the list mid-flight while another thread is iterating over it in `AddAndReplaceAllTLD()`. This immediately raises a `ConcurrentModificationException` or generates zero TLD domains. Re-reading the `TLD.txt` file from disk on every user input is also very wasteful.
- **Remediation:** Load `TLD_LIST` once, immutably (`Collections.unmodifiableSet`), at application startup, and remove the systematic call from the `JTypoSquatting` constructor.

---

### CONC-05: Severe freeze of the JavaFX Application Thread by blocking `sleep()` calls
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/service/PageAnalyzer.java` (lines 321-329, 341-357, 398-406)
- **Severity:** 🔴 **Critical**
- **Finding:**
  In `captureScreenshot()`, code runs on the JavaFX UI thread through `Platform.runLater()`:
  ```java
  webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
      if (newState == Worker.State.SUCCEEDED) {
          // Blocking busy wait on the JavaFX thread!
          for (int i = 0; i < 8; i++) {
              sleep(1000); // 8 SECONDS OF SLEEP ON THE JAVAFX THREAD!
          }
          ...
      }
  });
  ```
- **Impact:** `Platform.runLater` and the `Worker` listeners run on the single **JavaFX Application Thread**. By calling `Thread.sleep(1000)` 8 times, the JavaFX UI thread is completely frozen for 8 seconds per screenshot. No other rendering event, snapshot or page load can be processed during that time. Capturing 20 domains concurrently is therefore serialised and blocks the rendering engine for more than 160 seconds in total.
- **Remediation:** Use asynchronous transitions or a non-blocking JavaFX `PauseTransition` (`PauseTransition pause = new PauseTransition(Duration.seconds(2)); pause.setOnFinished(e -> takeSnapshot()); pause.play();`).

---

## 4. Functional Bugs and Regressions Identified

### BUG-01: `LogTailer` destroyed immediately at startup (try-with-resources, AutoCloseable)
- **Location:** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/ui/LogTailer.java` (lines 57-62, 117-119)
- **Severity:** 🔴 **Blocking for the log feature**
- **Finding:**
  The `start()` method declares:
  ```java
  try (ScheduledExecutorService logExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> ...)) {
      future = logExecutor.scheduleWithFixedDelay(() -> {
          ...
      }, 1000, 1000, TimeUnit.MILLISECONDS);
  }
  ```
  Since Java 19, `ExecutorService` implements the `AutoCloseable` interface.
- **Impact:** As soon as `start()` reaches the end of the `try` block, Java automatically calls `logExecutor.close()`, which triggers `shutdown()` and waits for the pool to stop. The executor is therefore destroyed a fraction of a second after being started. **The log tailer never runs in the background.**
- **Remediation:** Keep the `ScheduledExecutorService` as a member variable of the `LogTailer` class, do not use `try-with-resources`, and close it explicitly in the `stop()` method.

---

### BUG-02: Inconsistent status labels and status counters stuck at zero
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/service/DomainCheckService.java` (line 45) vs `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/ui/DomainStreamingService.java` (lines 112-113)
- **Severity:** 🟠 **Medium**
- **Finding:**
  On the backend side, `DomainCheckService` assigns the status:
  ```java
  String status = (pageData.getHttpCode() == 200) ? "Suspicious" : "Safe";
  // or "Unreachable"
  ```
  But on the frontend side, `DomainStreamingService` evaluates:
  ```java
  if ("Active".equals(result.getStatus())) activeCount.incrementAndGet();
  else if ("Dead".equals(result.getStatus())) deadCount.incrementAndGet();
  ```
- **Impact:** The `"Active"` and `"Dead"` statuses are never sent by the backend. As a result, `activeCount` and `deadCount` stay at **0** forever in the streaming service, which makes every displayed statistic wrong.
- **Remediation:** Harmonise the status constants in a shared `DomainStatus` enum (`SUSPICIOUS`, `SAFE`, `TESTING`, `UNREACHABLE`).

---

### BUG-03: Massive loss of spelling variants in `KeysValuesSwap`
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/util/JSonUtils.java` (lines 15-34)
- **Severity:** 🟠 **Medium (loss of algorithmic coverage)**
- **Finding:**
  The `common-misspellings.json` file contains `"misspelling": "correct_word"` pairs.
  To invert the dictionary, `KeysValuesSwap` inserts into a `JSONObject`:
  ```java
  String token = tokenizer.nextToken().strip();
  Object put = joOut.put(token, keyIn);
  ```
- **Impact:** When the correct word has several common misspellings (which is the case for hundreds of words, e.g. `"absence"`, which has `"abscence"`, `"absense"`, `"absance"`), each call to `joOut.put("absence", ...)` **overwrites** the previous misspelling. The dictionary keeps only one variant per word and destroys hundreds of them.
- **Remediation:** Use a multi-valued structure: `Map<String, List<String>>`.

---

### BUG-04: Generation of invalid domain names starting with a dot
- **Location:** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/generator/Dash.java` (lines 21, 47)
- **Severity:** 🟠 **Medium**
- **Finding:**
  In `Dash.java`, the domain is rebuilt with:
  ```java
  String subDomain = domainName.getSubDomain();
  String tld = domainName.getTLD();
  ...
  String newDomain = subDomain + '.' + sb + '.' + tld;
  ```
  If the user entered `example.com` (without a `www` subdomain), `domainName.getSubDomain()` returns `""`.
- **Impact:** `newDomain` becomes `"" + '.' + "ex-ample" + '.' + "com"` = **`.ex-ample.com`**.
  The generated domain starts with an illegal dot.
- **Remediation:** Check that the subdomain is present before inserting the dot:
  ```java
  String newDomain = (subDomain.isEmpty() ? "" : subDomain + ".") + sb + "." + tld;
  ```

---

### BUG-05: Faulty syntax validation in `DomainName`
- **Location:** `shared/src/main/java/com/aleph/graymatter/jtyposquatting/net/DomainName.java` (lines 33-35)
- **Severity:** 🟡 **Low to Medium**
- **Finding:**
  The validation method is:
  ```java
  private static boolean isValidDomain(String domainName) {
      return domainName.contains(".") && domainName.indexOf('.') != domainName.length() - 1;
  }
  ```
- **Impact:** Completely absurd strings such as `" . "`, `"http://a.b"`, `"foo..bar"`, `"127.0.0.1"` or `"-abc.com"` are considered valid domains, causing crashes further down in the generators or invalid HTTP requests.
- **Remediation:** Implement RFC 1035 / RFC 1123 validation based on a compliant regex or on the `InternetDomainName` class of Google Guava (already among the project dependencies).

---

### BUG-06: `ConfigManager.setLocale()` has no effect
- **Location:** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/config/ConfigManager.java` (lines 96-98)
- **Severity:** 🟡 **Low**
- **Finding:**
  ```java
  public void setLocale(Locale locale) {
      ResourceBundle.clearCache();
  }
  ```
- **Impact:** Clearing the cache changes neither the default locale of the JVM nor the reference of the `ResourceBundle messages` already created in the constructor. Changing the language in the UI has no effect at all.

---

### BUG-07: `ClientConfig` exposed to a `NullPointerException`
- **Location:** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/config/ClientConfig.java` (lines 23-28)
- **Severity:** 🟡 **Low**
- **Finding:**
  ```java
  props.load(ClientConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE));
  ```
  If `client.properties` is not on the classpath, `getResourceAsStream()` returns `null`. `props.load(null)` throws a `NullPointerException` that the `catch (IOException e)` block does not catch.

---

## 5. Technical Debt and Architectural Gaps

### DEBT-01: 17 algorithms announced but not implemented
- **Finding:** `Const.ALGO_NAME_LIST` proudly lists 21 typosquatting algorithms (`omission`, `repetition`, `changeOrder`, `vowelSwap`, `homophones`, `singularPluralize`, `numeralSwap`, etc.) as well as a `NUMERAL` table of digits. In reality, **only 4 algorithms** (`Dash`, `Homoglyph`, `Misspell`, `TLD`) exist in the project. The other 17 are only orphan declarative strings.

### DEBT-02: Dead code and unused DTOs
- `GenerationRequestDTO.java`: declared in the `shared` module, it has no custom constructor and no getters/setters, and is not injected anywhere in the project.
- `TypoSquattingController.java` (line 41): `private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();` is created but never referenced.
- `DatabaseService.java` (line 19): `private Server h2Server;` initialised to `null` and unused.

### DEBT-03: Redundant JSON libraries side by side
- The project includes both `com.googlecode.json-simple:json-simple:1.1.1` (an obsolete 2012 library without generics) and `com.google.code.gson:gson:2.10.1`, while also using Jackson under the hood with Spring Boot. This redundancy makes the binary heavier and complicates serialisation (e.g. `@SerializedName` and `transient` to handle Gson and Jackson in parallel).

### DEBT-04: Phantom Tomcat configuration parameters
- `backend/src/main/resources/application-production.properties` sets `server.tomcat.threads.max=200` and `server.tomcat.threads.min-spare=10`. However, `build.gradle` replaces Tomcat with Jetty (`spring-boot-starter-jetty`), so these properties are entirely ignored. Likewise, the `management.endpoints...` metrics are configured although `spring-boot-starter-actuator` is not included.

### DEBT-05: Inconsistent and verbose logging
- Many `System.out.println(...)` and `System.err.println(...)` calls in `DomainCheckService.java`, `PreviewPanel.java`, etc., instead of systematically using the SLF4J infrastructure (`LoggerFactory.getLogger(...)`).

---

## 6. Assessment of Coverage and Test Strategy

### TEST-01: No unit tests at all in `frontend` and `shared`
- The `frontend` and `shared` modules contain **no test file**. The algorithm generators, the utility classes and the streaming engine have no automated validation.

### TEST-02: Fake unit tests with external dependencies in the `backend`
- The `PageAnalyzerUnitTest.java` class has the `UnitTest` suffix, but sends real HTTP requests over the network to `www.aleph-networks.eu` and starts the JavaFX graphics stack.
- **Consequences:**
  - If the build machine has no Internet access, the tests fail.
  - If the remote site is slow or unavailable, the build is blocked.
  - If no X11 server is present (standard headless CI environment without Xvfb), the build freezes for 30 seconds per test before crashing.
- **Remediation:** Use mocks (`Mockito`) and a local test HTTP server (`MockWebServer` or `WireMock`) to test the HTML parsing and the analysis logic without any external dependency.

---

## 7. Prioritised Improvement Plan (Impact / Effort Matrix)

```
        ▲ Impact
        │
        │  [CONC-01] HikariCP pool        [CONC-05] Refactor JavaFX
        │  [CONC-02] Sync SSE emitter     [SEC-01] Secure SSL
        │  [BUG-01] Fix LogTailer         [TEST-02] Mocks / WireMock
        │  [BUG-03] Fix KeysValuesSwap
        │
        │  [BUG-02] Status enum           [DEBT-01] Implement algorithms
        │  [BUG-04] Fix Dash leading dot  [DEBT-03] Remove json-simple
        │  [SEC-02] Navigation warning
        │
        └────────────────────────────────────────────────────────► Effort
                 Low / Medium                       High
```

### 7.1 Immediate Actions (Priority 1 — Urgent)

1. **Replace the single JDBC connection with HikariCP / JdbcTemplate**:
   In `DatabaseService.java`, inject the `javax.sql.DataSource` managed by Spring Boot and remove the `Connection` instance variable.
2. **Fix the JavaFX blocking in `PageAnalyzer`**:
   Remove the blocking `sleep(1000)` calls running on the JavaFX Application Thread and use a non-blocking asynchronous mechanism.
3. **Fix `LogTailer`**:
   Remove the `try-with-resources` that closes the `ScheduledExecutorService` too early.
4. **Synchronise SSE sends**:
   Add a `synchronized (emitter)` lock in `sendSseEvent` to avoid write conflicts between Virtual Threads.
5. **Fix the loss of variants in `KeysValuesSwap`**:
   Change the structure to support several spelling variants per target word (`Map<String, List<String>>`).

### 7.2 Consolidation Actions (Priority 2 — Medium Term)

6. **Harmonise the domain statuses**:
   Create a `DomainStatus` enum in the `shared` module and synchronise the `activeCount` and `deadCount` counters of the frontend.
7. **Fix `Dash.java` and `DomainName.java`**:
   Fix the concatenation to avoid illegal leading dots, and harden the validation with Guava's `InternetDomainName`.
8. **Secure external viewing**:
   Add a confirmation pop-up with a security warning before opening a domain in the host browser.
9. **Replace the fake unit tests with isolated tests (WireMock)**:
   Decouple the unit tests from the Internet connection and create a complete test suite for the algorithms of the shared module.

### 7.3 Cleanup Actions (Priority 3 — Technical Debt)

10. **Remove `json-simple` and use only `Gson` / `Jackson`**.
11. **Clean up dead code**:
    - Remove `GenerationRequestDTO` or wire it to the endpoints.
    - Remove the leftover Tomcat properties from `application-production.properties`.
    - Replace every `System.out.println` with `logger.info()` / `logger.debug()`.
12. **Implement the 17 missing algorithms** to deliver the capabilities promised by the interface and the documentation.
