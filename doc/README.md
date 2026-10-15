# JTypoSquatting documentation

**Version:** 2.0-alpha1. The project overview, the list of generation algorithms and the quick starts are
in the [README](../README.md) at the project root.

JTypoSquatting comes in two forms:
- a **web service** for several users: Next.js frontend, Spring Boot API and workers, MySQL, S3 storage,
  OpenID Connect login;
- a **desktop application**: all-in-one JAR with a Swing window, running the API in local mode.

## Getting started

| Document | Content |
|----------|---------|
| [QUICK_START.md](QUICK_START.md) | Run the web service in development, or the desktop application |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | Known issues: Chromium, login, quotas, live results, tests, build |

## Using and integrating

| Document | Content |
|----------|---------|
| [API.md](API.md) | Authentication, scans, live results (SSE), quotas, screenshots, account; legacy desktop endpoints |
| [FUNCTIONAL_ARCHITECTURE.md](FUNCTIONAL_ARCHITECTURE.md) | Use cases and business flows of the web service and of the desktop application |

## Architecture and implementation

| Document | Content |
|----------|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Software architecture: components, deployment roles, architecture decisions |
| [TECHNICAL_ARCHITECTURE.md](TECHNICAL_ARCHITECTURE.md) | Technical and application architecture with diagrams |
| [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md) | Every generation algorithm with examples, class reference, database schema, configuration |

## Operating the service

| Document | Content |
|----------|---------|
| [DEPLOYMENT.md](DEPLOYMENT.md) | Build, configuration, containers, monitoring, backups and retention, CI/CD |
| [COMPLIANCE.md](COMPLIANCE.md) | GDPR register, retention periods, users' rights, abuse handling (draft for legal review) |

## Project history

| Document | Content |
|----------|---------|
| [AUDIT.md](AUDIT.md) | Code audit of the original desktop application; most findings are now fixed |
| [ACTION_PLAN.md](ACTION_PLAN.md) | Action plan towards the public web service and its progress |

## Keeping the documentation up to date

- Describe what the code does, and check names, endpoints, properties and default values in the code.
- Run the examples you document. Generation examples come from the actual generators.
- Update the README and the related documents in the same change as the code.
