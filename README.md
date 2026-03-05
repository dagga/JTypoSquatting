# JTypoSquatting v2.0_alpha1

Typo squatting domain detection tool for security analysts and brand protection professionals.

![TypoSquatting Example](https://github.com/hernic/JTypoSquatting/assets/4397039/874a5ff7-68d5-4d8b-9a60-a4dddde188f9)

## Quick Start

```bash
# Build and run
./gradlew :frontend:copyFatJar
java -jar JTypoSquatting.jar
```

**Requirements:** Java 21+ | [Full Installation Guide](doc/DEPLOYMENT.md)

---

## What It Does

JTypoSquatting generates potential typo squatted domains from a target domain and checks their status in real-time:

1. **Generate** typo variants using 4 algorithms (Dash, Homoglyph, Misspell, TLD)
2. **Check** each domain's HTTP status and capture screenshots
3. **Display** results with live updates and metrics
4. **Export** results for further analysis

![Screenshot](https://github.com/hernic/JTypoSquatting/assets/4397039/042a2ebf-2b8f-4950-b70f-e4e1717579c7)

---

## Documentation

| Document | Description |
|----------|-------------|
| **[Quick Start](doc/QUICK_START.md)** | Get running in 5 minutes |
| **[Deployment Guide](doc/DEPLOYMENT.md)** | Installation, configuration, CI/CD |
| **[API Documentation](doc/API.md)** | REST API reference |
| **[Troubleshooting](doc/TROUBLESHOOTING.md)** | Common issues and solutions |
| **[Architecture](doc/ARCHITECTURE.md)** | Software architecture |
| **[Functional Architecture](doc/FUNCTIONAL_ARCHITECTURE.md)** | Business functions |
| **[Technical Reference](doc/TECHNICAL_REFERENCE.md)** | Algorithms, mappings, class reference |

---

## Features

### Core
- ✅ Real-time domain checking with SSE streaming
- ✅ Screenshot capture for active domains
- ✅ Language detection (75+ languages)
- ✅ HTTP status and metadata extraction
- ✅ Local H2 database for persistence

### UI/UX
- ✅ Swing-based desktop application
- ✅ Live metrics (Generated, HTTP up, Inaccessible)
- ✅ Screenshot preview panel
- ✅ Log viewer (backend/frontend)
- ✅ Multi-language support (EN/FR)

### Performance
- ✅ Virtual Threads (Java 21+) for high concurrency
- ✅ Optimized JAR size (32 MB vs 108 MB)
- ✅ Batch processing with configurable delays

---

## Algorithms

| Algorithm | Description | Example |
|-----------|-------------|---------|
| **Dash** | Add/remove/move hyphens | `example.com` → `ex-ample.com` |
| **Homoglyph** | Visually similar characters | `example` → `ехаmple` (Cyrillic) |
| **Misspell** | Common spelling mistakes | `absence` → `absense` |
| **TLD** | Change/add TLDs | `example.eu` → `example.fr` |

**Complete reference:** [Technical Reference - Algorithms](doc/TECHNICAL_REFERENCE.md#2-domain-generation-algorithms)

---

## Build Commands

```bash
# Full build
./gradlew clean build

# Optimized fat JAR (32 MB)
./gradlew :frontend:fatJar

# Copy JAR to root
./gradlew :frontend:copyFatJar

# Native installer (requires jpackage)
./gradlew :frontend:packageApp
```

**Full build guide:** [Deployment - Building](doc/DEPLOYMENT.md#3-building-from-source)

---

## Configuration

### Quick Settings

| Setting | Default | File |
|---------|---------|------|
| API URL | `http://localhost:8080` | `client.properties` |
| Window Size | 1200x800 | `config.properties` |
| Max Parallel Checks | 10 | `config.properties` |

**Full configuration:** [Deployment - Configuration](doc/DEPLOYMENT.md#5-configuration)

### Virtual Threads (Java 21+)

```properties
# application.properties
spring.threads.virtual.enabled=true
spring.task.execution.pool.max-size=128
```

**Benefits:** 1000+ concurrent domain checks, ~1KB memory per thread

**Details:** [Architecture - Virtual Threads](doc/ARCHITECTURE.md#9-performance-architecture)

---

## Project Structure

```
JTypoSquatting/
├── backend/          # Spring Boot REST API + Virtual Threads
├── frontend/         # Swing UI + SSE client
├── shared/           # Domain generation + DTOs
├── doc/              # Complete documentation
├── .github/workflows # CI/CD pipeline
└── scripts/          # Utility scripts
```

**Architecture details:** [Architecture - Components](doc/ARCHITECTURE.md#4-system-components)

---

## CI/CD

GitHub Actions automatically:
- Builds on push/PR
- Creates optimized JAR
- Publishes releases on tag

**Create a release:**
```bash
git tag v2.0-alpha1
git push origin v2.0-alpha1
```

**Workflow:** [.github/workflows/build-and-release.yml](.github/workflows/build-and-release.yml)

---

## Common Issues

| Issue | Solution |
|-------|----------|
| Cannot connect to API | Start backend: `./gradlew :backend:bootRun` |
| Java version error | Install JDK 21+: `sudo apt install openjdk-21-jdk` |
| No screenshots | Check backend logs: `tail -f /tmp/jtypo-backend.log` |

**Full troubleshooting:** [TROUBLESHOOTING.md](doc/TROUBLESHOOTING.md)

---

## Version History

| Version | Date | Highlights |
|---------|------|------------|
| **2.0_alpha1** | 2025 | Virtual Threads, i18n, optimized JAR, Swing UI |
| **1.0** | - | Original version |

---

## License

See [LICENSE](LICENSE) file.

---

**Questions?** Check the [Troubleshooting Guide](doc/TROUBLESHOOTING.md) or create an issue on GitHub.
