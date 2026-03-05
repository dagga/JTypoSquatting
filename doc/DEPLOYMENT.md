# JTypoSquatting - Deployment Guide

**Version:** 2.0-alpha1  
**Last Updated:** March 2025

---

## Table of Contents

1. [System Requirements](#1-system-requirements)
2. [Installation Options](#2-installation-options)
3. [Building from Source](#3-building-from-source)
4. [Running the Application](#4-running-the-application)
5. [Configuration](#5-configuration)
6. [Native Installers](#6-native-installers)
7. [CI/CD with GitHub Actions](#7-cicd-with-github-actions)
8. [Troubleshooting](#8-troubleshooting)
9. [Uninstallation](#9-uninstallation)

---

## 1. System Requirements

### 1.1 Minimum Requirements

| Component | Requirement |
|-----------|-------------|
| **Operating System** | Windows 10, macOS 10.14+, Linux (Ubuntu 20.04+) |
| **Java Version** | JDK 21 or higher |
| **RAM** | 2 GB |
| **Disk Space** | 500 MB |
| **Display** | 1280x720 resolution |
| **Network** | Internet access for domain checking |

### 1.2 Recommended Requirements

| Component | Requirement |
|-----------|-------------|
| **Java Version** | JDK 21 LTS |
| **RAM** | 4 GB or more |
| **Disk Space** | 1 GB |
| **Display** | 1920x1080 resolution |
| **Network** | Broadband connection |

### 1.3 Java Installation

#### Windows

1. Download JDK 21 from [Oracle](https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.exe)
2. Run the installer
3. Verify installation:
   ```cmd
   java -version
   ```

#### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install openjdk-21-jdk
java -version
```

#### Linux (RHEL/CentOS/Fedora)

```bash
sudo dnf install java-21-openjdk-devel
java -version
```

#### macOS

```bash
brew install openjdk@21
java -version
```

---

## 2. Installation Options

### 2.1 Option 1: Standalone JAR (Recommended for Development)

**Pros:**
- Quick setup
- No installation required
- Easy to update

**Cons:**
- Requires Java installed separately
- Manual startup

**Steps:**

1. Download `JTypoSquatting.jar` from releases
2. Open terminal/command prompt
3. Run:
   ```bash
   java -jar JTypoSquatting.jar
   ```

### 2.2 Option 2: Native Installer (Recommended for Production)

**Pros:**
- Bundled Java runtime
- Native application experience
- Auto-start capability

**Cons:**
- Larger download size
- Platform-specific

**Available Formats:**
- Windows: `.msi` or `.exe`
- macOS: `.dmg`
- Linux: `.deb`, `.rpm`, or `.AppImage`

### 2.3 Option 3: Build from Source

See [Building from Source](#3-building-from-source) section.

---

## 3. Building from Source

### 3.1 Prerequisites

- Git
- JDK 21+
- Gradle (wrapper included)

### 3.2 Clone Repository

```bash
git clone https://github.com/hernic/JTypoSquatting.git
cd JTypoSquatting
```

### 3.3 Build Commands

**Build All Modules:**

```bash
./gradlew clean build
```

**Build Optimized Fat JAR:**

```bash
./gradlew :frontend:fatJar
```

**Build and Copy JAR to Root:**

```bash
./gradlew :frontend:copyFatJar
```

**Build Native Installer (requires jpackage):**

```bash
./gradlew :frontend:packageApp      # Linux app-image
./gradlew :frontend:packageAppMsi   # Windows MSI
./gradlew :frontend:packageAppExe   # Windows EXE
```

### 3.4 Build Output Locations

| Artifact | Location |
|----------|----------|
| Fat JAR | `frontend/build/libs/JTypoSquatting.jar` |
| Copied JAR | `JTypoSquatting.jar` (root) |
| Native Installer | `dist/` |

### 3.5 Build Optimization

The fat JAR is optimized to exclude unnecessary dependencies:

- **Language models** (197 MB) excluded from frontend
- **Result:** 32 MB instead of 108 MB

---

## 4. Running the Application

### 4.1 Quick Start Script

**Linux/macOS:**

```bash
./scripts/run.sh
```

**Windows:**

```cmd
scripts\run.bat
```

### 4.2 Manual Startup

**Terminal 1 - Start Backend:**

```bash
./gradlew :backend:bootRun
```

**Terminal 2 - Start Frontend:**

```bash
./gradlew :frontend:run
```

### 4.3 Running the JAR

```bash
# Basic execution
java -jar JTypoSquatting.jar

# With custom locale (French)
java -Duser.language=fr -Duser.country=FR -jar JTypoSquatting.jar

# With increased memory
java -Xmx2G -jar JTypoSquatting.jar
```

### 4.4 JVM Options

| Option | Description | Default |
|--------|-------------|---------|
| `-Duser.language` | UI language | `en` |
| `-Duser.country` | UI country | `US` |
| `-Xmx` | Maximum heap size | Auto |
| `-Djava.awt.headless` | Headless mode | `false` |

---

## 5. Configuration

### 5.1 Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| `application.properties` | `backend/src/main/resources/` | Backend settings |
| `config.properties` | `frontend/src/main/resources/` | Frontend settings |
| `messages.properties` | `frontend/src/main/resources/` | UI translations |
| `client.properties` | `frontend/src/main/resources/` | API client config |

### 5.2 Backend Configuration

**File:** `backend/src/main/resources/application.properties`

```properties
# Server Configuration
server.port=8080
server.servlet.context-path=/

# Virtual Threads (Java 21+)
spring.threads.virtual.enabled=true
spring.task.execution.pool.core-size=16
spring.task.execution.pool.max-size=128

# Jetty Configuration
server.jetty.threads.max=200
server.jetty.threads.min=8

# SSE Timeout (5 minutes)
spring.mvc.async.request-timeout=300000
```

### 5.3 Frontend Configuration

**File:** `frontend/src/main/resources/config.properties`

```properties
# API Configuration
api.default.url=http://localhost:8080
api.timeout.ms=10000

# Domain Testing
domain.testing.timeout.ms=5000
domain.batch.size=50
domain.batch.delay.ms=200
domain.max.parallel.checks=10

# UI Settings
ui.window.width=1200
ui.window.height=800
ui.table.row.height=24
ui.preview.width=320
ui.preview.height=240
ui.max.open.dialogs=5

# Logging
log.backend.path=/tmp/jtypo-backend.log
log.frontend.path=/tmp/jtypo-frontend.log
```

### 5.4 Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JAVA_HOME` | Java installation path | System default |
| `JPACKAGE_HOME` | jpackage installation path | `/usr/bin/jpackage` |

---

## 6. Native Installers

### 6.1 Prerequisites for jpackage

**Linux:**

```bash
# Ubuntu/Debian
sudo apt install dpkg-dev debhelper

# RHEL/Fedora
sudo dnf install rpm-build
```

**Windows:**
- WiX Toolset 3.x (for MSI)
- Visual Studio Build Tools (for EXE)

**macOS:**
- Xcode Command Line Tools

### 6.2 Building Native Installers

**Linux App Image:**

```bash
./gradlew :frontend:packageApp
# Output: dist/JTypoSquatting/
```

**Windows MSI:**

```bash
./gradlew :frontend:packageAppMsi
# Output: dist/JTypoSquatting-2.0-alpha1.msi
```

**Windows EXE:**

```bash
./gradlew :frontend:packageAppExe
# Output: dist/JTypoSquatting-2.0-alpha1.exe
```

### 6.3 Installation

**Windows:**
1. Double-click `.msi` or `.exe` file
2. Follow installation wizard
3. Application available in Start Menu

**Linux (.deb):**

```bash
sudo dpkg -i JTypoSquatting_2.0-alpha1_amd64.deb
```

**Linux (.rpm):**

```bash
sudo rpm -i JTypoSquatting-2.0-alpha1.x86_64.rpm
```

**macOS (.dmg):**

1. Open `.dmg` file
2. Drag application to Applications folder

---

## 7. CI/CD with GitHub Actions

### 7.1 Overview

JTypoSquatting uses GitHub Actions for automated:
- Building and testing
- Screenshot capture (with Xvfb)
- Release creation
- Artifact distribution

### 7.2 Workflow Configuration

**File:** `.github/workflows/build-and-release.yml`

**Triggers:**
- Push to `main` or `master` branch
- Pull requests
- Tag pushes (for releases)
- Manual trigger (`workflow_dispatch`)

**Jobs:**
1. **test** - Run unit and functional tests with Xvfb
2. **build** - Build project (depends on test)
3. **analyze-jar-size** - Report JAR size

### 7.3 Xvfb Configuration for Headless Testing

GitHub Actions runs in headless environment. Xvfb (X Virtual Framebuffer) provides virtual display:

```yaml
- name: Install Xvfb
  run: |
    sudo apt-get update
    sudo apt-get install -y xvfb libgtk-3-0 libxrender1 libxtst6 libxi6

- name: Set up virtual display
  run: |
    Xvfb :99 -screen 0 1280x1024x24 &
    export DISPLAY=:99
    echo "DISPLAY=:99" >> $GITHUB_ENV
    sleep 2  # Wait for Xvfb to start

- name: Verify Xvfb
  run: |
    ps aux | grep Xvfb
    echo "DISPLAY is: $DISPLAY"

- name: Run tests
  run: |
    export DISPLAY=:99
    ./gradlew test --no-daemon
```

### 7.4 Test Modes

Tests have two modes based on environment:

| Environment | Mode | Screenshot Requirement |
|-------------|------|----------------------|
| **CI (GitHub Actions)** | Strict | Required (Xvfb provided) |
| **Local (no display)** | Tolerant | Optional (tests pass) |
| **Local (with Xvfb)** | Tolerant | Optional |

**Detection:**
```java
boolean isCI = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;

if (isCI) {
    // Strict mode: screenshot required
    assertNotNull(result.getScreenshot());
} else {
    // Tolerant mode: screenshot optional
    if (result.getScreenshot() != null) {
        assertTrue(result.getScreenshot().length > 0);
    }
}
```

### 7.5 Running Tests Locally

**Without Xvfb (default):**
```bash
./gradlew test
# Tests pass even without screenshots
```

**With Xvfb (for full testing):**
```bash
# Install Xvfb
sudo apt install xvfb

# Start virtual display
Xvfb :99 -screen 0 1280x1024x24 &
export DISPLAY=:99

# Run tests
./gradlew test
```

### 7.6 Automated Build Process

```yaml
on:
  push:
    branches: [main]
    tags: [v*]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
      - run: ./gradlew clean build
      - run: ./gradlew :frontend:fatJar
```

### 7.7 Creating a Release

1. **Update version in `gradle.properties`:**
   ```properties
   version=2.0-alpha1
   ```

2. **Commit and tag:**
   ```bash
   git add gradle.properties
   git commit -m "Release version 2.0-alpha1"
   git tag v2.0-alpha1
   git push origin v2.0-alpha1
   ```

3. **GitHub Actions will:**
   - Build the project
   - Create optimized JAR
   - Create GitHub Release
   - Attach JAR artifacts

### 7.8 Release Artifacts

After successful build, artifacts are available:
- `JTypoSquatting-{version}.jar` - Versioned JAR
- `JTypoSquatting.jar` - Latest JAR

---

## 8. Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for detailed troubleshooting, especially:
- [Testing Issues](TROUBLESHOOTING.md#5-testing-issues)
- [CI/CD Issues](TROUBLESHOOTING.md#6-cicd-issues)

### 8.1 Common Issues

#### Issue: "Cannot connect to API server"

**Cause:** Backend not running

**Solution:**
```bash
# Start backend first
./gradlew :backend:bootRun

# Then start frontend in another terminal
./gradlew :frontend:run
```

#### Issue: "Java 21 not found"

**Cause:** Wrong Java version in PATH

**Solution:**
```bash
# Check Java version
java -version

# Set JAVA_HOME if needed
export JAVA_HOME=/path/to/jdk-21
export PATH=$JAVA_HOME/bin:$PATH
```

#### Issue: "Out of memory"

**Cause:** Insufficient heap for screenshots

**Solution:**
```bash
java -Xmx2G -jar JTypoSquatting.jar
```

#### Issue: "Screenshots not displaying"

**Cause:** JavaFX runtime issue

**Solution:**
1. Ensure JDK 21+ with JavaFX
2. Check backend logs for screenshot errors
3. Verify display server is running

#### Issue: Build fails with "Virtual Threads not supported"

**Cause:** Java version < 21

**Solution:**
```bash
# Upgrade to Java 21
sudo apt install openjdk-21-jdk  # Ubuntu/Debian
```

### 8.2 Log Files

| Log | Location | Purpose |
|-----|----------|---------|
| Backend | `/tmp/jtypo-backend.log` | Backend API logs |
| Frontend | `/tmp/jtypo-frontend.log` | Frontend UI logs |

**View Logs:**

```bash
tail -f /tmp/jtypo-backend.log
tail -f /tmp/jtypo-frontend.log
```

### 8.3 Debug Mode

**Enable Debug Logging:**

```bash
# Backend
java -Dlogging.level.com.aleph.graymatter=DEBUG -jar backend.jar

# Frontend
java -Dlogging.level.com.aleph.graymatter=DEBUG -jar JTypoSquatting.jar
```

---

## 9. Uninstallation

### 9.1 JAR Installation

Simply delete the JAR file:

```bash
rm JTypoSquatting.jar
```

### 9.2 Native Installer (Windows)

1. Open Control Panel → Programs and Features
2. Find "JTypoSquatting"
3. Click Uninstall

### 9.3 Native Installer (Linux)

**Debian/Ubuntu:**

```bash
sudo apt remove jtyposquatting
```

**RHEL/Fedora:**

```bash
sudo dnf remove JTypoSquatting
```

### 9.4 Clean All Data

```bash
# Remove logs
rm /tmp/jtypo-*.log

# Remove database (if created)
rm -rf ~/.jtyposquatting/
```

---

## Appendix A: File Locations

| File/Directory | Purpose |
|----------------|---------|
| `JTypoSquatting.jar` | Executable JAR |
| `backend/build/libs/` | Backend JARs |
| `frontend/build/libs/` | Frontend JARs |
| `dist/` | Native installers |
| `/tmp/jtypo-*.log` | Log files |

---

## Appendix B: Port Usage

| Port | Service | Configurable |
|------|---------|--------------|
| 8080 | Backend API | Yes (`server.port`) |

---

## Appendix C: Quick Reference

```bash
# Build
./gradlew clean build

# Run (development)
./gradlew :backend:bootRun    # Terminal 1
./gradlew :frontend:run       # Terminal 2

# Run (production)
java -jar JTypoSquatting.jar

# Create release
git tag v2.0-alpha1 && git push origin v2.0-alpha1
```

---

*End of Document*
