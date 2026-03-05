# JTypoSquatting - Quick Start Guide

**Version:** 2.0-alpha1  
**Time to First Run:** 5 minutes

---

## Prerequisites

- **Java 21 or higher** installed
  - Check: `java -version`
  - Download: https://www.oracle.com/java/technologies/downloads/

---

## Option 1: Run Pre-built JAR (Fastest)

### Step 1: Download

Download `JTypoSquatting.jar` from the [releases page](https://github.com/hernic/JTypoSquatting/releases).

### Step 2: Run

```bash
java -jar JTypoSquatting.jar
```

### Step 3: Start Backend

Open a **second terminal** and run:

```bash
# Navigate to project directory
cd /path/to/JTypoSquatting

# Start backend
./gradlew :backend:bootRun
```

### Step 4: Use the Application

1. Enter a domain (e.g., `www.example.com`)
2. Click **Generate**
3. Watch results stream in real-time
4. Click on domains to see screenshots

---

## Option 2: Build from Source

### Step 1: Clone Repository

```bash
git clone https://github.com/hernic/JTypoSquatting.git
cd JTypoSquatting
```

### Step 2: Build

```bash
./gradlew clean build :frontend:copyFatJar
```

### Step 3: Run

```bash
# Terminal 1 - Backend
./gradlew :backend:bootRun

# Terminal 2 - Frontend
java -jar JTypoSquatting.jar
```

---

## First Domain Check

### Example: Check www.google.com

1. **Enter Domain:**
   ```
   Domain Name: www.google.com
   ```

2. **Click Generate**

3. **Watch Results:**
   ```
   Generated: 150 | HTTP up: 45 | Inaccessible: 105
   ```

4. **View Details:**
   - Double-click any domain
   - See screenshot, HTTP headers, metadata

---

## Common Tasks

### Copy Domains to Clipboard

1. Select domains in table (Ctrl+Click for multiple)
2. Click **Copy** button or press `Ctrl+C`
3. Paste in browser or text editor

### Clear Results

Click **Clear** button to reset everything.

### View Logs

- **Backend tab:** API logs
- **Frontend tab:** UI logs

---

## Troubleshooting

### "Cannot connect to API"

**Problem:** Backend not running

**Solution:**
```bash
# Start backend first
./gradlew :backend:bootRun
```

### "Java not found"

**Problem:** Java 21+ not installed

**Solution:**
```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Check installation
java -version
```

### No Screenshots

**Problem:** JavaFX runtime issue

**Solution:**
- Ensure JDK 21+ includes JavaFX
- Check backend logs for errors

---

## Next Steps

- Read [User Guide](USER_GUIDE.md) for detailed usage
- Check [API Documentation](API.md) for REST API details
- See [Configuration](DEPLOYMENT.md#5-configuration) for customization

---

## Quick Reference

```bash
# Build
./gradlew clean build

# Run backend
./gradlew :backend:bootRun

# Run frontend
java -jar JTypoSquatting.jar

# Create release JAR
./gradlew :frontend:fatJar :frontend:copyFatJar
```

---

*For complete documentation, see the [doc/](doc/) directory.*
