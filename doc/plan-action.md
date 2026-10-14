# Plan d'Action — JTypoSquatting : vers un service web public multi-utilisateurs

## 1. Introduction

Ce document remplace le plan d'action initial, issu du rapport d'audit ([`audit.md`](audit.md)).

JTypoSquatting évolue d'une application de bureau mono-utilisateur (JAR tout-en-un Spring Boot + Swing) vers un **site web public utilisé par plusieurs utilisateurs en parallèle**. Le backend Java est conservé ; l'interface Swing est remplacée par un frontend **Next.js**.

Ce changement de cible transforme plusieurs faiblesses tolérables en local (API sans authentification, annulation globale, exécution de pages hostiles dans le processus de l'API) en **bloquants pour une mise en production**.

## 2. Objectifs

- Ouvrir le service au public sans exposer l'infrastructure (SSRF, exécution de contenu hostile, abus).
- Supporter plusieurs utilisateurs simultanés avec des analyses isolées, persistantes et annulables individuellement.
- Offrir une interface web moderne (vitrine publique référencée + application temps réel).
- Finaliser l'assainissement de la dette technique identifiée par l'audit.

## 3. Architecture cible

```
Navigateur ─► Reverse proxy (Traefik / nginx, TLS) ─┬─ /        ─► Next.js (pages publiques SSG + application client)
                                                    └─ /api/*   ─► API Spring Boot ─┬─► MySQL 8.4
                                                                                    ├─► Stockage objet S3 / MinIO (captures)
                                                                                    └─► Table de jobs ─► Workers d'analyse
                                                                                                        (Playwright + Chromium headless,
                                                                                                         réseau isolé, sortie Internet uniquement)
```

| Sujet | Choix | Justification |
|---|---|---|
| Frontend | Next.js (App Router, TypeScript) | Vitrine publique avec SEO (SSG) + application client dans un même projet |
| Flux temps réel | SSE **directement** vers Spring via le reverse proxy | Éviter la mise en tampon / les timeouts d'un proxy Next.js sur des flux longs |
| Authentification | OIDC (Keycloak ou IdP managé) ; **Spring en client OAuth2 (pattern BFF)**, session par cookie `httpOnly` | `EventSource` ne permet pas d'envoyer d'en-tête `Authorization` : un cookie de session même origine est la solution naturelle pour le SSE |
| Sessions | Spring Session JDBC (MySQL) | Sessions partagées entre plusieurs instances de l'API |
| Base de données | **MySQL 8.4 LTS** (InnoDB, `utf8mb4`) + Flyway | Voir contraintes §3.1 |
| Captures d'écran | Playwright for Java + Chromium headless | Remplace JavaFX WebView : plus de dépendance X11/Xvfb, isolation possible, rendu fidèle |
| File de jobs | Table MySQL + `SELECT … FOR UPDATE SKIP LOCKED` | Suffisant pour la V1 ; RabbitMQ envisageable si la charge l'exige |

### 3.1 Contraintes spécifiques MySQL

- **Collation des noms de domaine :** la collation par défaut `utf8mb4_0900_ai_ci` est insensible à la casse **et aux accents** : `exämple.com` et `example.com` y sont égaux, ce qui provoquerait des collisions d'index uniques et des faux positifs de cache pour les variantes homoglyphes.
  - Clé technique : forme **ASCII/punycode** (`IDN.toASCII`) en `VARCHAR(253) CHARACTER SET ascii COLLATE ascii_bin`.
  - Forme Unicode stockée séparément, uniquement pour l'affichage (`utf8mb4_bin`).
- **Métadonnées de page** (map `metadata`) : colonne `JSON`.
- **Captures** : jamais en `BLOB` ; seule la clé de l'objet S3 est stockée.
- **Tests** : Testcontainers MySQL (le mode compatibilité MySQL de H2 n'est pas fidèle).
- **Driver / migrations** : `com.mysql:mysql-connector-j`, Flyway avec le module `flyway-mysql`.

## 4. Bilan du plan précédent

| Réf. | Action | Statut | Remarque |
|---|---|---|---|
| 3.1 | Connexion JDBC unique → `JdbcTemplate` | ✅ Fait | `DatabaseService` utilise `JdbcTemplate` |
| 3.2 | Blocage JavaFX dans `PageAnalyzer` | ✅ Fait | Devient sans objet avec Playwright (P1.3) |
| 3.3 | Réparation de `LogTailer` | ✅ Fait | Composant Swing, retiré à terme (P3.7) |
| 3.4 | Synchronisation des émissions SSE | ✅ Fait | `synchronized (emitter)` en place |
| 3.5 | Perte de variantes dans `KeysValuesSwap` | ✅ Fait | `KeysValuesSwapMulti` (`Map<String, List<String>>`) |
| 4.1 | Énumération `DomainStatus` | ✅ Fait | |
| 4.2 | Correction `Dash` / `DomainName` | ✅ Fait | Validation via `InternetDomainName` |
| 4.3 | Avertissement avant ouverture d'un domaine | ⏳ À faire | Reporté côté web (P3.5) |
| 4.4 | Tests isolés (WireMock) | 🟡 Partiel | WireMock sur `PageAnalyzerUnitTest` ; à étendre (P2.7) |
| 5.1 | Suppression de `json-simple` | 🟡 Partiel | Plus utilisé dans le code ; `lib/json-simple-1.1.1.jar` à supprimer |
| 5.2 | Code mort `GenerationRequestDTO` | ⏳ À faire | À réutiliser comme corps de `POST /api/scans` (P2.2) |
| 5.3 | Propriétés Tomcat résiduelles | ⏳ À faire | `application-production.properties` l. 34-35 |
| 5.4 | `System.out.println` → logger | 🟡 Partiel | 5 occurrences restantes |
| 5.5 | 17 algorithmes manquants | ⏳ À faire | Conservé en Phase 0 |

## 4bis. Avancement (2026-10-14, branche `feature/web-public`)

| Action | Statut | Remarques |
|---|---|---|
| P0.1 Actions restantes de l'audit | ✅ | |
| P0.2 Algorithmes manquants | ✅ | Les 21 algorithmes ; énumération `Algorithm` |
| P1.1 Authentification | ✅ | Découverte OIDC paresseuse ; mode `local` pour l'application de bureau |
| P1.2 Anti-SSRF | ✅ | + correctif : domaines internationalisés convertis en punycode avant la requête |
| P1.3 Playwright | ✅ | Chromium dans le conteneur worker ; isolation réseau dans le compose (P4.1) |
| P1.4 Validation TLS | ✅ | |
| P1.5 Anti-abus | 🟡 | Débit, quotas, plafonds, User-Agent : faits. Captcha d'inscription : à activer dans Keycloak (clés du domaine) |
| P1.6 En-têtes, CORS | ✅ | |
| P2.1 MySQL + Flyway | ✅ | Migrations par moteur (MySQL, H2 pour le bureau et les tests) |
| P2.2 Jobs persistants | ✅ | **Écart** : la table `scans` sert de file (réclamation par `UPDATE` conditionnel, portable) au lieu d'une table `scan_jobs` avec `SKIP LOCKED` |
| P2.3 SSE par analyse, annulation | ✅ | Anciens endpoints chargés en mode bureau uniquement |
| P2.4 Cache partagé | ✅ | |
| P2.5 Stockage objet | ✅ | S3 quelconque (tests avec S3Mock : les images MinIO ne sont plus publiées) ou système de fichiers |
| P2.6 API / workers | ✅ | Les workers gardent un serveur HTTP réduit à la supervision (P4.3) |
| P2.7 Tests backend | ✅ | `./gradlew test` sans Internet ; tests réseau via `-Dinclude.network=true` |
| P3.1 Next.js et pages publiques | ✅ | Next.js 16.4, pré-rendu statique |
| P3.2 Authentification frontend | ✅ | Déconnexion renvoyant l'URL du fournisseur en JSON |
| P3.3 Application d'analyse | ✅ | Testée avec 5000 résultats |
| P3.4 Internationalisation | ✅ | Livrée avec P3.1 (next-intl) |
| P3.5 Consultation sécurisée | ✅ | Livrée avec P3.3 |
| P3.6 Tests de bout en bout | ✅ | Playwright avec backend simulé, en CI |
| P3.7 Retrait de Swing | ⏸️ | **Non fait** : décision produit (suppression de l'application de bureau et des installeurs) à valider |
| P4.1 Conteneurs, reverse proxy | 🟡 | Images, nginx et compose validés avec Podman. Restent : TLS, règles de pare-feu de l'hôte pour le réseau des workers |
| P4.2 CI/CD | 🟡 | Tests et images publiées sur GHCR ; déploiement à scripter selon l'hébergement |
| P4.3 Observabilité | ✅ | Prometheus, logs JSON, règles d'alerte ; collecteur et Prometheus à déployer |
| P4.4 Sauvegardes, rétention | ✅ | Scripts testés ; planification à mettre en place sur l'hôte |
| P4.5 Conformité | 🟡 | Export et suppression de compte faits ; `doc/CONFORMITE.md` et textes légaux à valider par le juridique |

Critères de mise en production (§ 10) : techniquement couverts, sauf les textes légaux (juridique), la
supervision effective (déploiement de Prometheus) et les sauvegardes planifiées sur l'hôte.

## 5. Phase 0 — Finaliser l'assainissement

### P0.1 Clôturer les actions restantes de l'audit

- **Description :** Supprimer `lib/json-simple-1.1.1.jar` ; supprimer les propriétés `server.tomcat.*` ; remplacer les 5 `System.out.println` restants par SLF4J.
- **Critère d'acceptation :** `grep` sans résultat sur `json-simple`, `server.tomcat`, `System.out.println` dans `src/main`.
- **Responsable :** Développeur Backend
- **Estimation :** 0,5 j
- **Échéance :** [Date]

### P0.2 Implémenter les algorithmes manquants

- **Description :** Implémenter les 17 algorithmes déclarés dans `Const.ALGO_NAME_LIST` (`omission`, `repetition`, `vowelSwap`, `homophones`, `numeralSwap`, etc.) ou retirer ceux qui ne seront pas livrés. Chaque algorithme est couvert par des tests unitaires.
- **Critère d'acceptation :** aucun nom d'algorithme orphelin ; couverture de test sur chaque générateur.
- **Responsable :** Développeur Backend
- **Estimation :** 5-8 j (peut être mené en parallèle des phases suivantes)
- **Échéance :** [Date]

## 6. Phase 1 — Sécurité et fondations (bloquant avant ouverture publique)

### P1.1 Authentification et autorisation

- **Description :** Mettre en place un fournisseur OIDC ; configurer Spring Security en client OAuth2 (login, logout, session cookie `httpOnly`, `Secure`, `SameSite=Lax`) ; protéger toutes les routes `/api/**` sauf la santé et les contenus publics ; protection CSRF sur les requêtes modifiantes.
- **Critère d'acceptation :** tout appel non authentifié à `/api/scans/**` renvoie 401 ; un utilisateur ne peut lire ni annuler l'analyse d'un autre (403).
- **Responsable :** Développeur Backend
- **Estimation :** 4-5 j
- **Échéance :** [Date]

### P1.2 Protection anti-SSRF du moteur d'analyse

- **Description :** Avant toute connexion (analyse HTTP et capture) :
  - Résoudre le DNS puis rejeter les plages privées, loopback, link-local, réservées et de métadonnées cloud (IPv4 et IPv6, dont `169.254.169.254`, `fd00::/8`, `::ffff:` mappées).
  - Se connecter à l'IP validée (anti DNS-rebinding).
  - Limiter aux ports 80/443 et revalider chaque saut de redirection (nombre de redirections borné).
- **Critère d'acceptation :** tests automatisés couvrant un domaine résolvant vers chaque catégorie d'IP interdite, une redirection vers une IP interne et un cas de rebinding.
- **Responsable :** Développeur Backend
- **Estimation :** 3-4 j
- **Échéance :** [Date]

### P1.3 Remplacement de JavaFX WebView par Playwright isolé

- **Description :** Implémenter `ScreenshotService` avec Playwright for Java (Chromium headless). Les workers s'exécutent dans des conteneurs dédiés : réseau sans accès interne (sortie Internet uniquement), limites CPU/mémoire, timeout par page, contexte navigateur neuf par domaine, pas de téléchargements. Supprimer les dépendances JavaFX et les options `--add-opens` / Xvfb associées.
- **Critère d'acceptation :** captures fonctionnelles sans serveur X ; un worker ne peut joindre ni l'API, ni MySQL, ni le réseau interne.
- **Responsable :** Développeur Backend / DevOps
- **Estimation :** 4-5 j
- **Échéance :** [Date]

### P1.4 Validation TLS maîtrisée (audit SEC-01)

- **Description :** Supprimer le `TrustManager` et le `HostnameVerifier` permissifs de `PageAnalyzer`. Effectuer une connexion avec validation standard ; en cas d'échec, enregistrer le statut du certificat (valide / auto-signé / expiré / nom invalide) comme **signal d'analyse** plutôt que de masquer l'erreur.
- **Critère d'acceptation :** aucun `TrustManager` permissif dans le code ; statut TLS visible dans les résultats.
- **Responsable :** Développeur Backend
- **Estimation :** 1-2 j
- **Échéance :** [Date]

### P1.5 Anti-abus

- **Description :** Limitation de débit par utilisateur et par IP (Bucket4j) ; quotas (analyses par jour, domaines par analyse, analyses simultanées) ; politesse par domaine cible (concurrence et fréquence bornées) ; captcha à l'inscription ; User-Agent identifiable avec page d'information.
- **Critère d'acceptation :** dépassement de quota → 429 avec message explicite ; métriques de quota exposées.
- **Responsable :** Développeur Backend
- **Estimation :** 2-3 j
- **Échéance :** [Date]

### P1.6 En-têtes de sécurité et CORS

- **Description :** Servir frontend et API sur la même origine via le reverse proxy et supprimer la configuration CORS de `WebConfig` (actuellement incohérente : origine `:8080`, API `:8081`). Ajouter CSP, HSTS, `X-Content-Type-Options`, `Referrer-Policy`.
- **Critère d'acceptation :** score A sur un scanner d'en-têtes (ex. securityheaders.com).
- **Responsable :** Développeur Backend / DevOps
- **Estimation :** 1 j
- **Échéance :** [Date]

## 7. Phase 2 — Modèle d'exécution et données

### P2.1 Migration H2 → MySQL 8.4

- **Description :** Schéma versionné Flyway : `users`, `scans`, `scan_results`, `domain_checks` (cache partagé), `scan_jobs`, tables Spring Session. Respect des contraintes du §3.1 (punycode + `ascii_bin`, `JSON`, pas de `BLOB`). Pool HikariCP dimensionné. Retirer H2 et le fichier `typosquatting_db.mv.db` du dépôt.
- **Critère d'acceptation :** application démarrée sur MySQL via Docker Compose ; tests d'intégration Testcontainers verts.
- **Responsable :** Développeur Backend
- **Estimation :** 3-4 j
- **Échéance :** [Date]

### P2.2 Analyses sous forme de jobs persistants

- **Description :** `POST /api/scans` (corps : `GenerationRequestDTO`, ce qui résout l'ancienne action 5.2) crée l'analyse et renvoie un `scanId`. Les workers prennent les tâches avec `SELECT … FOR UPDATE SKIP LOCKED`. Cycle de vie : `PENDING → RUNNING → COMPLETED | CANCELLED | FAILED`. Une analyse survit à la fermeture de l'onglet et au redémarrage d'une instance.
- **Critère d'acceptation :** un redémarrage de worker en cours d'analyse n'entraîne ni perte ni double traitement.
- **Responsable :** Développeur Backend
- **Estimation :** 4-5 j
- **Échéance :** [Date]

### P2.3 Flux SSE par analyse et annulation ciblée

- **Description :** `GET /api/scans/{id}/events` avec reprise via `Last-Event-ID` ; `DELETE /api/scans/{id}` annule **uniquement** cette analyse après vérification du propriétaire. Supprimer `/api/cancel` et `/api/cancel-and-clear`, qui agissent aujourd'hui sur toutes les sessions et vident toute la base.
- **Critère d'acceptation :** deux utilisateurs simultanés ; l'annulation de l'un n'affecte pas l'autre ; reconnexion SSE sans perte d'événements.
- **Responsable :** Développeur Backend
- **Estimation :** 2-3 j
- **Échéance :** [Date]

### P2.4 Cache partagé des vérifications

- **Description :** Réutiliser le résultat d'un domaine déjà vérifié récemment (durée de validité configurable, ex. 24 h), quel que soit l'utilisateur, avec possibilité de forcer une revérification.
- **Critère d'acceptation :** deux analyses successives du même domaine ne déclenchent qu'une seule série de requêtes sortantes dans la fenêtre de validité.
- **Responsable :** Développeur Backend
- **Estimation :** 1-2 j
- **Échéance :** [Date]

### P2.5 Stockage objet des captures

- **Description :** Stocker les captures dans S3 / MinIO ; exposer `GET /api/screenshots/{id}` (contrôle d'accès, ou URL signée à durée limitée). Les événements SSE ne transportent plus de `byte[]` en base64 mais une URL.
- **Critère d'acceptation :** taille moyenne d'un événement SSE < 2 Ko.
- **Responsable :** Développeur Backend
- **Estimation :** 2 j
- **Échéance :** [Date]

### P2.6 Séparation API / workers

- **Description :** Même base de code, deux profils Spring (`api`, `worker`) déployables et dimensionnables indépendamment.
- **Critère d'acceptation :** ajout d'une instance de worker sans redéploiement de l'API.
- **Responsable :** Développeur Backend / DevOps
- **Estimation :** 1-2 j
- **Échéance :** [Date]

### P2.7 Stratégie de tests backend

- **Description :** Étendre WireMock à tous les appels HTTP sortants (fin de l'ancienne action 4.4) ; Testcontainers MySQL et MinIO pour l'intégration ; tests de concurrence multi-utilisateurs.
- **Critère d'acceptation :** `./gradlew test` passe sans accès Internet.
- **Responsable :** Développeur Backend
- **Estimation :** 3 j
- **Échéance :** [Date]

## 8. Phase 3 — Frontend Next.js

### P3.1 Initialisation et pages publiques

- **Description :** Projet Next.js (App Router, TypeScript, ESLint) ; pages publiques générées statiquement : accueil, fonctionnement des algorithmes, FAQ, mentions légales, CGU, politique de confidentialité. Métadonnées SEO, sitemap.
- **Responsable :** Développeur Frontend
- **Estimation :** 4-5 j
- **Échéance :** [Date]

### P3.2 Intégration de l'authentification

- **Description :** Parcours connexion / déconnexion / inscription redirigeant vers les endpoints Spring (BFF) ; récupération de l'utilisateur courant via `GET /api/me` ; routes applicatives protégées.
- **Responsable :** Développeur Frontend
- **Estimation :** 2 j
- **Échéance :** [Date]

### P3.3 Application d'analyse

- **Description :**
  - Formulaire de lancement.
  - Tableau temps réel virtualisé (TanStack Table + virtualisation) alimenté par SSE avec reconnexion automatique.
  - Compteurs (générés, actifs, inaccessibles), aperçu des captures, fiche détaillée d'un domaine, historique des analyses de l'utilisateur, annulation, export CSV/JSON.
- **Critère d'acceptation :** parité fonctionnelle avec l'interface Swing ; fluidité avec 5 000 lignes.
- **Responsable :** Développeur Frontend
- **Estimation :** 8-10 j
- **Échéance :** [Date]

### P3.4 Internationalisation

- **Description :** FR/EN via `next-intl`, sur les pages publiques et l'application.
- **Responsable :** Développeur Frontend
- **Estimation :** 1-2 j
- **Échéance :** [Date]

### P3.5 Consultation sécurisée des domaines (ancienne action 4.3 / audit SEC-02)

- **Description :** Page intermédiaire d'avertissement avant ouverture d'un domaine analysé (risque de contenu malveillant) ; liens en `rel="noopener noreferrer"` ; la capture d'écran est proposée comme alternative sûre.
- **Responsable :** Développeur Frontend
- **Estimation :** 0,5 j
- **Échéance :** [Date]

### P3.6 Tests de bout en bout

- **Description :** Scénarios Playwright : connexion, lancement d'analyse, réception du flux, annulation, export, quotas.
- **Responsable :** Développeur Frontend
- **Estimation :** 2-3 j
- **Échéance :** [Date]

### P3.7 Retrait de l'interface Swing

- **Description :** Après validation de la parité, supprimer le module `frontend` Swing, `CombinedLauncher`, `LogTailer` et les tâches jpackage associées.
- **Responsable :** Développeur Backend
- **Estimation :** 1 j
- **Échéance :** [Date]

## 9. Phase 4 — Infrastructure, exploitation et conformité

### P4.1 Conteneurisation et reverse proxy

- **Description :** Images Docker (API, worker, Next.js) ; `docker compose` de développement (MySQL, MinIO, Keycloak) ; reverse proxy avec TLS. Configuration SSE : `proxy_buffering off`, timeouts de lecture longs.
- **Responsable :** DevOps
- **Estimation :** 2-3 j
- **Échéance :** [Date]

### P4.2 CI/CD

- **Description :** Étendre les workflows GitHub Actions existants : build et tests (Testcontainers), lint et tests E2E du frontend, construction et publication des images, déploiement.
- **Responsable :** DevOps
- **Estimation :** 2 j
- **Échéance :** [Date]

### P4.3 Observabilité

- **Description :** Spring Actuator + Micrometer (Prometheus) : durée des analyses, taille de la file de jobs, erreurs, quotas atteints, consommation des workers. Logs JSON centralisés (remplace le visualiseur de logs Swing). Alertes sur la saturation de la file et le taux d'erreur.
- **Responsable :** DevOps
- **Estimation :** 2 j
- **Échéance :** [Date]

### P4.4 Sauvegardes et rétention

- **Description :** Sauvegardes MySQL automatisées avec test de restauration ; purge planifiée des analyses et des captures au-delà de la durée de conservation définie.
- **Responsable :** DevOps
- **Estimation :** 1 j
- **Échéance :** [Date]

### P4.5 Conformité

- **Description :** RGPD (registre des traitements, durées de conservation, droits d'accès et de suppression du compte) ; CGU avec politique d'usage acceptable ; procédure de signalement et de retrait de contenu ; surveillance de la réputation des IP de sortie des workers.
- **Responsable :** Juridique / Product Owner
- **Estimation :** à définir
- **Échéance :** [Date]

## 10. Critères de mise en production publique (go / no-go)

- [ ] Authentification obligatoire et cloisonnement des analyses par utilisateur (P1.1, P2.3)
- [ ] Protection SSRF testée, y compris redirections et DNS rebinding (P1.2)
- [ ] Workers isolés du réseau interne (P1.3)
- [ ] Aucun `TrustManager` permissif (P1.4)
- [ ] Quotas et limitation de débit actifs (P1.5)
- [ ] Sauvegardes MySQL testées (P4.4)
- [ ] CGU, politique de confidentialité et mentions légales publiées (P4.5)
- [ ] Supervision et alertes opérationnelles (P4.3)

## 11. Estimation et séquencement

| Phase | Charge (1 personne) | Dépendances |
|---|---|---|
| Phase 0 — Assainissement | 1 sem. (+ algorithmes en parallèle) | — |
| Phase 1 — Sécurité et fondations | 3-4 sem. | — |
| Phase 2 — Exécution et données | 3-4 sem. | P1.1 |
| Phase 3 — Frontend Next.js | 4-5 sem. | P3.1 démarrable immédiatement ; P3.3 dépend de P2.2-P2.5 |
| Phase 4 — Infra et conformité | 1-2 sem. (+ juridique) | P4.1 dès le début de la Phase 2 |
| **Total** | **≈ 12-16 semaines** (≈ 7-9 avec un dev backend et un dev frontend en parallèle) | |

## 12. Risques principaux

| Risque | Impact | Mitigation |
|---|---|---|
| Exploitation du moteur d'analyse (SSRF, faille navigateur) | Critique | P1.2, P1.3, mises à jour régulières de Chromium |
| Abus du service (scan massif, proxy gratuit) | Élevé | P1.5, CGU, supervision |
| IP de sortie des workers blacklistées | Moyen | Politesse par cible, User-Agent identifiable, IP de sortie dédiée |
| Coût mémoire de Chromium (≈ 200-500 Mo par contexte) | Moyen | Pool de navigateurs borné, mise à l'échelle des workers |
| Collisions de domaines homoglyphes en base | Moyen | Punycode + collation binaire (§3.1) |
| Glissement de périmètre (algorithmes, fonctionnalités) | Moyen | Parité Swing comme périmètre V1, le reste en V2 |

---

**Révision :** 2026-10-14
**Responsable :** [Nom]
