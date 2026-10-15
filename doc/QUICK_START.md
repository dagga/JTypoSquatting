# JTypoSquatting — Quick Start

**Version:** 2.0

JTypoSquatting comes in two forms:

- **the web service**: several users, OpenID Connect login, Next.js interface;
- **the desktop application**: a single user, Swing window, everything in one JAR.

---

## Requirements

| Tool | Version | For |
|------|---------|-----|
| Java (JDK) | 21 to 24 (Gradle 9.0 does not run on Java 25) | Backend, desktop application |
| Node.js | 22 | Web frontend |
| Docker or Podman | recent | Keycloak (and MySQL if needed) in development, Testcontainers tests |

Headless Chromium takes the screenshots. Install it once:

```bash
./gradlew :backend:installPlaywrightBrowsers
```

Without Chromium, scans still run, but without screenshots.

```bash
git clone https://github.com/dagga/JTypoSquatting.git
cd JTypoSquatting
```

---

## Web service (development)

```bash
# 1. Identity provider: Keycloak on http://localhost:8180
#    (realm jtypo, user alice / alice, administration admin / admin)
docker compose up -d keycloak          # or: podman compose up -d keycloak

# 2. Backend: API and worker in one process on :8081, in-memory H2 database
FORWARD_HEADERS_STRATEGY=framework ./gradlew :backend:bootRun

# 3. Frontend on http://localhost:3000 (/api, /oauth2, /login and /logout are forwarded to :8081)
cd web
npm install
npm run dev
```

Open http://localhost:3000, click **Log in** (`alice` / `alice`), then start a scan from **My scans**.

- `FORWARD_HEADERS_STRATEGY=framework` makes the login come back to port 3000 rather than 8081.
- Use `localhost`, not the IP address of the machine: Keycloak only accepts the return URLs
  `localhost:3000`, `localhost:3001`, `localhost:8081` and `localhost`.
- If port 3000 is taken, run `npx next dev -p 3001`: that port is allowed too.

**With MySQL instead of H2:**

```bash
docker compose up -d mysql
DB_URL=jdbc:mysql://localhost:3306/jtypo DB_USER=jtypo DB_PASSWORD=jtypo-dev \
FORWARD_HEADERS_STRATEGY=framework ./gradlew :backend:bootRun
```

Flyway creates the schema at startup.

---

## Desktop application

```bash
./gradlew :frontend:fatJar
java -jar frontend/build/libs/JTypoSquatting.jar
```

The JAR starts the backend in *local* mode (API on 127.0.0.1:8081, no login), then opens the window.

1. Type a domain (e.g. `example.com`) and start the scan.
2. Results appear live.
3. Double-click a domain to see its screenshot, HTTP headers and metadata.

---

## A scan in brief

- About 1 800 variants are generated for `example.com`, by 21 algorithms. A scan checks at most 50 000
  domains.
- Each variant is contacted: HTTP response, TLS certificate, title, language, screenshot.
- A check less than 24 hours old is taken from the cache, unless you tick "Ignore the cache".
- Per-user quotas on the web service: 2 simultaneous scans and 50 per 24 hours.

---

## Tests

```bash
./gradlew test                          # Backend, without Internet access
./gradlew test -Dinclude.network=true   # Also the tests that contact real websites

cd web
npm run lint && npm run typecheck
npm run test:e2e                        # Playwright, production build, simulated backend
```

The MySQL and S3 tests (Testcontainers) only run when Docker or Podman is available. With rootless Podman:

```bash
systemctl --user start podman.socket
DOCKER_HOST=unix:///run/user/$UID/podman/podman.sock TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test
```

---

## If something goes wrong

| Symptom | Lead |
|---------|------|
| `java: command not found`, or Gradle refuses the JDK | Install a JDK 21–24; check `org.gradle.java.home` in `gradle.properties` |
| No screenshots | `./gradlew :backend:installPlaywrightBrowsers` |
| Login fails or loops | Go through `localhost:3000`; check that Keycloak runs on 8180 |

More details: [TROUBLESHOOTING.md](TROUBLESHOOTING.md). Deployment: [DEPLOYMENT.md](DEPLOYMENT.md).
