# Compliance — JTypoSquatting (public web service)

> **Technical draft, to be validated by the legal team and the DPO before going live.**
> It describes what the software actually does. Items marked _[to be completed]_ are decisions of the
> publisher.

## 1. Record of processing activities (GDPR, art. 30)

| Item | Content |
|---|---|
| Controller | _[to be completed: company name, address, contact]_ |
| DPO / contact | _[to be completed]_ |
| Purpose | Provide a typosquatting detection service: generate the variants of a domain name, check them, keep the history of the user's scans |
| Legal basis | Performance of a contract (terms of use accepted at sign-up); legitimate interest for the security of the service (logs, rate limiting) |
| Data subjects | Registered users |
| Data | OpenID Connect identifier, username, e-mail address (provided by the identity provider); creation and last visit dates; scans started (scanned domain, dates, status) and their results; HTTP session; IP address and requests in the technical logs |
| Non-personal data | Cached domain checks (public third-party sites, shared between users) and screenshots of these sites |
| Recipients | Operations team of the publisher; processors: hosting, S3 object storage, identity provider _[to be completed with their guarantees and data location]_ |
| Transfers outside the EU | _[to be completed according to the chosen processors]_ |
| Security | See § 5 |

## 2. Retention periods

| Data | Period | Mechanism |
|---|---|---|
| Scans and results | 90 days after creation | Nightly purge (`jtypo.retention.scan-days`) |
| Cached checks | 30 days | Nightly purge (`jtypo.retention.check-days`) |
| Screenshots | As long as a scan result or a cached check references them | Purge of orphan screenshots |
| HTTP session | 8 hours of inactivity | `spring.session.timeout` |
| Account (profile) | Until the user deletes it | `DELETE /api/me`; _[to be decided: deletion of inactive accounts]_ |
| MySQL backups | 14 most recent backups | `deploy/backup/mysql-backup.sh` (`KEEP`) |
| Technical logs | _[to be decided, e.g. 12 months]_ | Depends on the log collector |

## 3. Data subjects' rights

| Right | Implementation |
|---|---|
| Access, portability | "My scans › My data › Export": `GET /api/me/export`, complete JSON (profile, scans, results) |
| Erasure | "Delete my account": `DELETE /api/me` deletes the profile, the scans, the results and the screenshots no other user needs, ends the other sessions, then logs out (including from the identity provider) |
| Rectification | Profile managed by the identity provider; updated at the next login |
| Identity provider account | Managed separately (Keycloak or the chosen provider): _[to be explained to users]_ |
| Backups | Erased data disappears from the backups after rotation (14 days) |

## 4. Terms of use: points to cover

- Allowed use: protecting one's own brands and domains, or those of a client who mandated it.
- Forbidden: probing third-party sites at scale, bypassing the quotas (several accounts, automation), using
  the results to harm others, attacking the infrastructure.
- Quotas: 2 simultaneous scans, 50 per 24 hours, 120 requests per minute, up to 50,000 domains per scan
  (all configurable).
- The service visits the generated domains and keeps screenshots of them: liability for third-party
  content _[legal wording]_.
- Account suspension or deletion in case of abuse.

The "Terms of use", "Privacy" and "Legal notice" pages of the site contain placeholder texts to replace
(`web/messages/fr.json` and `en.json`).

## 5. Security (summary)

- OpenID Connect login with PKCE, session in an `httpOnly` cookie, `Secure` behind HTTPS,
  `SameSite=Lax`, CSRF token, security headers (CSP, HSTS…).
- Isolation: a user only accesses their own scans, results and screenshots.
- Analysis engine: anti-SSRF policy (no internal address), strict TLS validation, Chromium in containers
  isolated from the internal network, without privileges.
- Rate limiting and quotas; monitoring and alerts (`deploy/prometheus`).
- Tested backups; production data never used in development.

## 6. Abuse reports and takedown

- **Contact**: publish an `abuse@` address _[to be completed]_ on the site and in the User-Agent of the
  service (`jtypo.outbound.user-agent`, e.g.
  `Mozilla/5.0 (compatible; JTypoSquatting/2.0; +https://<site>/en/faq)`).
- **Reception**: acknowledge within 2 business days _[to be validated]_.
- **Third-party site asking to stop the visits**: add the domain to an exclusion list
  _[feature to develop if the need is confirmed]_; cached checks expire within 30 days.
- **Removing a screenshot**: remove the reference in the database
  (`UPDATE scan_results SET screenshot_key = NULL WHERE screenshot_key = ?`, and the same in
  `domain_checks`), then delete the S3 object.
- **Abusive user**: delete their account (procedure of § 3) and block them at the identity provider.
- Keep a log of the requests and of the actions taken.

## 7. Reputation of the outbound IP addresses

Workers contact many domains, including malicious sites: their outbound IP addresses may be reported.

- Use outbound IP addresses dedicated to the workers, separate from those of the site and of e-mails.
- Monitor these addresses on reputation lists (Spamhaus, AbuseIPDB…) _[tool to choose]_.
- The application already limits its footprint: at most 64 simultaneous checks per instance, 4 connections
  per host, identifiable User-Agent with a contact.
