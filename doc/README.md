# JTypoSquatting Documentation

**Version:** 2.0-alpha1

---

## Documentation Index

This directory contains complete documentation for JTypoSquatting.

### Getting Started

| Document | Description | Read Time |
|----------|-------------|-----------|
| [QUICK_START.md](QUICK_START.md) | Get running in 5 minutes | 5 min |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Installation and configuration | 15 min |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | Common issues and solutions | As needed |

### Architecture & Design

| Document | Description | Audience |
|----------|-------------|----------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Software architecture (components, patterns, technology) | Architects, Developers |
| [FUNCTIONAL_ARCHITECTURE.md](FUNCTIONAL_ARCHITECTURE.md) | Functional architecture (use cases, business logic) | Analysts, Product Owners |

### Technical Reference

| Document | Description | Audience |
|----------|-------------|----------|
| [API.md](API.md) | REST API and SSE documentation | API Consumers, Integrators |
| [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md) | Algorithms, mappings, class reference | Developers |

---

## Quick Links

### For New Users
1. Start with [QUICK_START.md](QUICK_START.md)
2. Refer to [TROUBLESHOOTING.md](TROUBLESHOOTING.md) if issues occur

### For Developers
1. Read [ARCHITECTURE.md](ARCHITECTURE.md) for system overview
2. Check [API.md](API.md) for API details
3. Use [TECHNICAL_REFERENCE.md](TECHNICAL_REFERENCE.md) for implementation details

### For DevOps
1. Follow [DEPLOYMENT.md](DEPLOYMENT.md) for installation
2. Check CI/CD section for automated builds

---

## Documentation Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    JTypoSquatting Docs                      │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
┌───────▼────────┐   ┌────────▼────────┐   ┌──────▼───────┐
│   Getting      │   │   Architecture  │   │   Technical  │
│   Started      │   │   & Design      │   │   Reference  │
└───────┬────────┘   └────────┬────────┘   └──────┬───────┘
        │                     │                     │
   ┌────┴────┐           ┌────┴────┐           ┌────┴────┐
   │         │           │         │           │         │
   ▼         ▼           ▼         ▼           ▼         ▼
QUICK   DEPLOYMENT   ARCHITECT.  FUNCTIONAL   API    TECHNICAL
START                            ARCHITECT.          REFERENCE
   │
   ▼
TROUBLE-
SHOOTING
```

---

## Related Files

| File | Location | Purpose |
|------|----------|---------|
| README.md | Project root | Project overview and quick reference |
| build.gradle | Project root | Build configuration |
| gradle.properties | Project root | Version and project settings |
| .github/workflows/ | GitHub Actions | CI/CD pipeline |

---

## Contributing to Documentation

When updating documentation:

1. **Keep README.md concise** - Reference detailed docs instead of duplicating content
2. **Update related docs together** - Changes in code may require doc updates
3. **Follow markdown conventions** - Consistent formatting across all docs
4. **Test commands and examples** - Ensure all code snippets work as documented

---

## Document Changelog

| Date | Document | Changes |
|------|----------|---------|
| March 2025 | All | Initial comprehensive documentation for v2.0-alpha1 |

---

*For questions or documentation issues, please create a GitHub issue.*
