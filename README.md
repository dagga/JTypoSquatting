# JTypoSquatting v2.0_alpha1

The goal of this project is to create a list of potentially **typo squatted domains** for checking with the Free tool **Aleph Search Clear** (or another one): https://www.aleph-networks.eu/en/our-search-engines/aleph-search-clear/

You can ask for access here: https://www.aleph-networks.eu/en/contact/

![image](https://github.com/hernic/JTypoSquatting/assets/4397039/874a5ff7-68d5-4d8b-9a60-a4dddde188f9)

- This project is an enhanced version of https://github.com/typosquatter/ail-typo-squatting rewritten in Java.
- **Version 2.0_alpha1** features a new Swing-based UI with real-time domain checking, screenshot previews, and internationalization support.

![image](https://github.com/hernic/JTypoSquatting/assets/4397039/042a2ebf-2b8f-4950-b70f-e4e1717579c7)

## Requirements

- **Java 17 or higher** is required
- On Windows: https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.exe
- On Linux: `sudo apt install openjdk-17-jdk` or similar

## Quick Start

### Option 1: Run the standalone JAR (Recommended)

```bash
# Build the executable JAR (32 MB optimized)
./gradlew :frontend:fatJar

# Run the application
java -jar frontend/build/libs/JTypoSquatting.jar
```

Or copy the JAR to the project root:

```bash
./gradlew :frontend:copyFatJar
java -jar JTypoSquatting.jar
```

### Option 2: Run from source (Development)

Launch both backend and frontend separately:

```bash
# Terminal 1 - Start the backend API
./gradlew :backend:bootRun

# Terminal 2 - Start the frontend UI
./gradlew :frontend:run
```

### Option 3: Native Installer (jpackage)

For a native application with installer:

```bash
# Linux app-image
./gradlew :frontend:packageApp

# Windows MSI installer (requires Windows)
./gradlew :frontend:packageAppMsi

# Windows EXE installer (requires Windows)
./gradlew :frontend:packageAppExe
```

## Usage

1. Enter a domain name (e.g., `www.example.com`)
2. Click **Generate** to create typo-squatted variants
3. The application will automatically check each domain and display:
   - **Status**: Suspicious (red), Safe (green), or Testing (orange)
   - **HTTP Code**: Response code from the domain
   - **Title**: Page title if accessible
   - **Language**: Detected language with flag icon
   - **Preview**: Screenshot of the domain (when available)

### Metrics Display

The bottom-left corner displays real-time metrics:
- **Generated domains**: Total number of generated domains
- **HTTP up**: Number of accessible domains (responding with HTTP codes)
- **Inaccessible**: Number of unreachable/dead domains

## Internationalization (i18n)

The application supports multiple languages. Currently available:
- **English** (default)
- **French**

To change the language, set the locale when running:

```bash
# English
java -Duser.language=en -Duser.country=US -jar JTypoSquatting.jar

# French
java -Duser.language=fr -Duser.country=FR -jar JTypoSquatting.jar
```

## Configuration

Application configuration is stored in `config.properties`. Key settings:

```properties
# API Configuration
api.default.url=http://localhost:8080
api.timeout.ms=10000

# Domain Testing
domain.testing.timeout.ms=5000
domain.batch.size=50
domain.max.parallel.checks=10

# UI Settings
ui.window.width=1200
ui.window.height=800
ui.max.open.dialogs=5
```

## Algorithms

- **Dash**: Adding, removing, and moving dashes in the domain name  
  (e.g., `www.aleph-networks.eu` → `www.alephnetworks.eu`)
  
- **Homoglyphs**: Replacing characters with visually similar ones  
  (e.g., `www.aleph-networks.eu` → `www.αleph-networks.eu`)
  
- **Misspells**: Replacing correctly spelled words with common misspellings (English only)  
  (e.g., `www.absence.com` → `www.absense.com`)
  
- **TLD**: Changing TLDs and adding TLD suffixes (creates company name as subdomain)  
  (e.g., `www.aleph-networks.eu` → `www.aleph-networks.fr` or `www.aleph-networks.eu.com`)

## Project Structure

```
JTypoSquatting/
├── backend/          # Spring Boot REST API
├── frontend/         # Swing GUI application
├── shared/           # Common code (domain generation, language detection)
├── scripts/          # Utility scripts
├── doc/              # Documentation files
├── .github/          # GitHub Actions workflows
├── JTypoSquatting.jar    # Standalone executable (after build)
└── build.gradle      # Gradle build configuration
```

## Building

```bash
# Build all modules
./gradlew build

# Build only the standalone JAR (optimized, ~32 MB)
./gradlew :frontend:fatJar

# Build and copy JAR to project root
./gradlew :frontend:copyFatJar

# Create native installer
./gradlew :frontend:packageApp
```

### JAR Size Optimization

The fat JAR is optimized by excluding unnecessary language models:
- **Before optimization**: ~108 MB
- **After optimization**: ~32 MB (70% reduction)

Language detection models are only included in the backend where they're needed.

## CI/CD

GitHub Actions workflow automatically:
- Builds the project on every push
- Runs tests
- Creates optimized fat JAR
- Publishes releases on tag push (tags starting with `v`)

To create a release:
```bash
git tag v2.0-alpha1
git push origin v2.0-alpha1
```

## Documentation

See the [doc/](doc/) directory for detailed documentation:

- [Quick Start Guide](doc/QUICK_START.md)
- [Architecture](doc/ARCHITECTURE.md)
- [Deployment](doc/DEPLOYMENT.md)
- [Troubleshooting](doc/TROUBLESHOOTING.md)

## Version History

- **v2.0_alpha1** (2025): Complete rewrite with Swing UI, i18n support, optimized JAR
- **v1.0**: Original version

## License

See [LICENSE](LICENSE) file for details.
