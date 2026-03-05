# JTypoSquatting - Troubleshooting Guide

**Version:** 2.0-alpha1  
**Last Updated:** March 2025

---

## Table of Contents

1. [Connection Issues](#1-connection-issues)
2. [Build Issues](#2-build-issues)
3. [Runtime Issues](#3-runtime-issues)
4. [Screenshot Issues](#4-screenshot-issues)
5. [Performance Issues](#5-performance-issues)
6. [Database Issues](#6-database-issues)
7. [Internationalization Issues](#7-internationalization-issues)
8. [FAQ](#8-faq)

---

## 1. Connection Issues

### Error: "Cannot connect to API server"

**Symptoms:**
- Frontend shows error dialog on startup
- Console displays: `ERROR: Cannot connect to API at http://localhost:8080`

**Causes:**
1. Backend is not running
2. Backend is running on different port
3. Firewall blocking connection

**Solutions:**

```bash
# 1. Check if backend is running
curl http://localhost:8080/api/data/stats

# 2. Start backend if not running
./gradlew :backend:bootRun

# 3. Check backend logs
tail -f /tmp/jtypo-backend.log

# 4. Verify port configuration
# In backend/src/main/resources/application.properties
server.port=8080
```

### Error: "Connection refused"

**Symptoms:**
- Backend fails to start
- Port already in use

**Solutions:**

```bash
# Check what's using port 8080
lsof -i :8080  # Linux/macOS
netstat -ano | findstr :8080  # Windows

# Kill the process or change port
# In application.properties:
server.port=8081
```

---

## 2. Build Issues

### Error: "Java 21 not found"

**Symptoms:**
```
FAILURE: Build failed with an exception.
* What went wrong:
Could not determine the dependencies of task ':backend:bootJar'.
> Failed to calculate dependencies for task ':backend:bootJar'
```

**Solutions:**

```bash
# Check Java version
java -version

# Set JAVA_HOME
export JAVA_HOME=/path/to/jdk-21
export PATH=$JAVA_HOME/bin:$PATH

# Ubuntu/Debian installation
sudo apt install openjdk-21-jdk

# macOS installation
brew install openjdk@21
```

### Error: "Virtual threads not supported"

**Symptoms:**
```
java.lang.UnsupportedOperationException: Virtual threads are not supported
```

**Cause:** Java version < 21

**Solution:** Upgrade to Java 21 or higher

### Error: Gradle build fails

**Symptoms:**
```
BUILD FAILED in XXs
```

**Solutions:**

```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies

# Check Gradle version
./gradlew --version

# Update Gradle wrapper
./gradlew wrapper --gradle-version=9.0
```

---

## 3. Runtime Issues

### Error: "Out of memory"

**Symptoms:**
- Application crashes during domain checking
- `java.lang.OutOfMemoryError: Java heap space`

**Solutions:**

```bash
# Increase heap size
java -Xms512M -Xmx2G -jar JTypoSquatting.jar

# Recommended for large domain lists (>500)
java -Xms1G -Xmx4G -jar JTypoSquatting.jar
```

### Error: "Too many open files"

**Symptoms:**
```
java.io.IOException: Too many open files
```

**Cause:** System file descriptor limit too low

**Solutions:**

```bash
# Check current limit
ulimit -n  # Linux/macOS

# Increase limit temporarily
ulimit -n 4096

# Increase limit permanently (Linux)
# Add to /etc/security/limits.conf
* soft nofile 4096
* hard nofile 8192
```

### Error: Application freezes

**Symptoms:**
- UI becomes unresponsive
- No domain updates

**Solutions:**

1. **Check backend logs:**
   ```bash
   tail -f /tmp/jtypo-backend.log
   ```

2. **Restart both components:**
   ```bash
   # Stop both terminals
   # Start backend first
   ./gradlew :backend:bootRun
   
   # Then frontend
   java -jar JTypoSquatting.jar
   ```

---

## 4. Screenshot Issues

### Error: "No screenshot data available"

**Symptoms:**
- Preview panel shows "No preview available"
- Logs show: `PreviewPanel: No screenshot data available`

**Causes:**
1. HTTP code is not 200
2. JavaFX runtime error
3. Screenshot capture timeout

**Solutions:**

```bash
# 1. Check HTTP code in results table
# Screenshots only captured for HTTP 200

# 2. Check backend logs for JavaFX errors
grep -i "javafx\|screenshot" /tmp/jtypo-backend.log

# 3. Verify JavaFX is available
java --list-modules | grep javafx

# 4. Increase screenshot timeout
# In PageAnalyzer.java, increase Thread.sleep(3000) to 5000
```

### Error: Black screenshots

**Symptoms:**
- Screenshot captured but image is black

**Cause:** JavaFX rendering issue

**Solutions:**

```bash
# Add JVM options for software rendering
java -Dprism.order=sw -Dprism.vsync=false -jar JTypoSquatting.jar

# Or disable hardware acceleration
java -Dsun.java2d.opengl=false -jar JTypoSquatting.jar
```

---

## 5. Performance Issues

### Slow domain checking

**Symptoms:**
- Domains checked one at a time
- Long wait for results

**Solutions:**

```properties
# Enable virtual threads (application.properties)
spring.threads.virtual.enabled=true
spring.task.execution.pool.max-size=128

# Increase parallel checks
domain.max.parallel.checks=20
```

### High memory usage

**Symptoms:**
- Memory usage grows continuously
- Application becomes sluggish

**Solutions:**

```bash
# Limit concurrent screenshots
# In config.properties:
domain.max.parallel.checks=10

# Enable GC logging for analysis
java -Xlog:gc* -jar JTypoSquatting.jar

# Clear results periodically
# Click "Clear" button after processing batches
```

---

## 6. Database Issues

### Error: "Database locked"

**Symptoms:**
```
org.h2.jdbc.JdbcSQLSyntaxErrorException: Table locked
```

**Solutions:**

```bash
# Stop application
# Delete database file
rm ~/jtyposquatting/db.*

# Restart application
# Database will be recreated
```

### Error: "Disk full"

**Symptoms:**
```
java.io.IOException: No space left on device
```

**Cause:** Too many screenshots stored

**Solutions:**

```bash
# Clear database
curl -X DELETE http://localhost:8080/api/data/all

# Or manually delete
rm -rf ~/jtyposquatting/

# Limit screenshot storage
# In PageAnalyzer.java, reduce screenshot quality
```

---

## 7. Internationalization Issues

### UI shows "!" keys "!"

**Symptoms:**
- Labels show `!label.domain.name!` instead of proper text

**Cause:** Resource bundle not found

**Solutions:**

```bash
# Verify resource files exist
ls frontend/src/main/resources/messages*.properties

# Check file encoding (should be UTF-8)
file frontend/src/main/resources/messages.properties

# Rebuild application
./gradlew clean :frontend:fatJar
```

### Wrong language displayed

**Symptoms:**
- UI shows French when English expected

**Solutions:**

```bash
# Force English locale
java -Duser.language=en -Duser.country=US -jar JTypoSquatting.jar

# Force French locale
java -Duser.language=fr -Duser.country=FR -jar JTypoSquatting.jar
```

---

## 8. FAQ

### Q: How many domains can be checked at once?

**A:** With virtual threads, 100-1000+ domains can be checked concurrently. Limited by:
- Available memory (screenshots)
- Network bandwidth
- Target server rate limits

### Q: Can I check domains without screenshots?

**A:** Yes, modify `PageAnalyzer.java`:
```java
// Comment out screenshot capture
// data.setScreenshot(captureScreenshot(url));
data.setScreenshot(null);
```

### Q: How do I add custom misspellings?

**A:** Edit `JTypoSquatting.java`:
```java
MISSPELLINGS.put("yourword", List.of("misspelling1", "misspelling2"));
```

### Q: Can I run backend on different machine?

**A:** Yes, update `client.properties`:
```properties
api.url=http://remote-server:8080
```

### Q: How do I export all results?

**A:** Use the API:
```bash
curl http://localhost:8080/api/data/all > results.json
```

### Q: Where are logs stored?

**A:** Default locations:
- Backend: `/tmp/jtypo-backend.log`
- Frontend: `/tmp/jtypo-frontend.log`

### Q: How do I reset all settings?

**A:** Delete configuration files:
```bash
rm /tmp/jtypo-*.log
rm -rf ~/.jtyposquatting/
```

---

## Appendix: Diagnostic Commands

```bash
# Check Java version
java -version

# Check if backend is running
curl http://localhost:8080/api/data/stats

# View backend logs
tail -f /tmp/jtypo-backend.log

# View frontend logs
tail -f /tmp/jtypo-frontend.log

# Check port usage
lsof -i :8080

# Check memory usage
jps -v | grep JTypoSquatting

# Test domain check API
curl "http://localhost:8080/api/data/analyze?domain=www.example.com"
```

---

## Getting Help

If issues persist:

1. **Check logs** in `/tmp/jtypo-*.log`
2. **Search issues** on GitHub
3. **Create issue** with:
   - Java version
   - OS version
   - Error messages
   - Log excerpts

---

*End of Document*
