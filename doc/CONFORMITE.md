# Conformité — JTypoSquatting (service web public)

> **Brouillon technique, à valider par le service juridique et le DPO avant la mise en production.**
> Il décrit ce que fait réellement le logiciel ; les éléments marqués _[à compléter]_ relèvent d'une
> décision de l'éditeur.

## 1. Registre des traitements (RGPD, art. 30)

| Rubrique | Contenu |
|---|---|
| Responsable de traitement | _[à compléter : raison sociale, adresse, contact]_ |
| DPO / contact | _[à compléter]_ |
| Finalité | Fournir un service de détection de typosquatting : génération de variantes d'un nom de domaine, vérification de ces variantes, historique des analyses de l'utilisateur |
| Base légale | Exécution du contrat (CGU acceptées à l'inscription) ; intérêt légitime pour la sécurité du service (journaux, limitation de débit) |
| Personnes concernées | Utilisateurs inscrits |
| Données | Identifiant OpenID Connect, nom d'utilisateur, adresse e-mail (fournis par le fournisseur d'identité) ; date de création et de dernière visite ; analyses lancées (domaine analysé, dates, statut) et leurs résultats ; session HTTP ; adresse IP et requêtes dans les journaux techniques |
| Données non personnelles | Vérifications de domaines en cache (sites publics tiers, partagées entre utilisateurs) et captures d'écran de ces sites |
| Destinataires | Équipe d'exploitation de l'éditeur ; sous-traitants : hébergeur, stockage objet S3, fournisseur d'identité _[à compléter avec leurs garanties et la localisation des données]_ |
| Transferts hors UE | _[à compléter selon les sous-traitants retenus]_ |
| Sécurité | Voir § 5 |

## 2. Durées de conservation

| Donnée | Durée | Mécanisme |
|---|---|---|
| Analyses et résultats | 90 jours après création | Purge nocturne (`jtypo.retention.scan-days`) |
| Vérifications en cache | 30 jours | Purge nocturne (`jtypo.retention.check-days`) |
| Captures d'écran | Tant qu'une analyse ou une vérification y fait référence | Purge des captures orphelines |
| Session HTTP | 8 heures d'inactivité | `spring.session.timeout` |
| Compte (profil) | Jusqu'à suppression par l'utilisateur | `DELETE /api/me` ; _[à décider : suppression des comptes inactifs]_ |
| Sauvegardes MySQL | 14 dernières sauvegardes | `deploy/backup/mysql-backup.sh` (`KEEP`) |
| Journaux techniques | _[à décider, ex. 12 mois]_ | Selon le collecteur de logs |

## 3. Droits des personnes

| Droit | Mise en œuvre |
|---|---|
| Accès, portabilité | « Mes analyses › Mes données › Exporter » : `GET /api/me/export`, JSON complet (profil, analyses, résultats) |
| Effacement | « Supprimer mon compte » : `DELETE /api/me` supprime le profil, les analyses, les résultats et les captures qui ne servent à aucun autre utilisateur, ferme les autres sessions, puis déconnecte (y compris du fournisseur d'identité) |
| Rectification | Profil géré par le fournisseur d'identité ; mis à jour à la connexion suivante |
| Compte du fournisseur d'identité | Géré séparément (Keycloak ou fournisseur choisi) : _[à préciser à l'utilisateur]_ |
| Sauvegardes | Les données effacées disparaissent des sauvegardes après rotation (14 jours) |

## 4. Conditions d'utilisation : points à couvrir

- Usage autorisé : protection de ses propres marques et domaines, ou de ceux d'un client mandant.
- Interdits : sondage massif de sites tiers, contournement des quotas (comptes multiples, automatisation),
  usage des résultats pour nuire, tentative d'atteinte à l'infrastructure.
- Quotas : 2 analyses simultanées, 50 par 24 heures, 120 requêtes par minute (modifiables).
- Le service visite les domaines générés et en conserve des captures : responsabilité quant au contenu
  de sites tiers _[rédaction juridique]_.
- Suspension ou suppression de compte en cas d'abus.

Les pages « Conditions d'utilisation », « Confidentialité » et « Mentions légales » du site contiennent
des textes provisoires à remplacer (`web/messages/fr.json` et `en.json`).

## 5. Sécurité (résumé)

- Authentification OpenID Connect, session en cookie `httpOnly`, `Secure` derrière HTTPS, `SameSite=Lax`,
  jeton CSRF, en-têtes de sécurité (CSP, HSTS…).
- Cloisonnement : un utilisateur n'accède qu'à ses analyses, résultats et captures.
- Moteur d'analyse : politique anti-SSRF (aucune adresse interne), validation TLS stricte, Chromium dans
  des conteneurs isolés du réseau interne, sans privilèges.
- Limitation de débit et quotas ; supervision et alertes (`deploy/prometheus`).
- Sauvegardes testées ; données de production jamais utilisées en développement.

## 6. Signalement d'abus et retrait de contenu

- **Contact** : publier une adresse `abuse@` _[à compléter]_ sur le site et dans le User-Agent du service
  (`jtypo.outbound.user-agent`, par ex. `Mozilla/5.0 (compatible; JTypoSquatting/2.0; +https://<site>/fr/faq)`).
- **Réception** : accusé de réception sous 48 h ouvrées _[à valider]_.
- **Site tiers qui demande l'arrêt des visites** : ajouter le domaine à une liste d'exclusion
  _[fonctionnalité à développer si le besoin se confirme]_ ; les vérifications en cache expirent sous 30 jours.
- **Retrait d'une capture** : supprimer la référence en base (`UPDATE scan_results SET screenshot_key = NULL
  WHERE screenshot_key = ?` et idem dans `domain_checks`), puis l'objet S3 correspondant.
- **Utilisateur abusif** : supprimer son compte (procédure du § 3) et le bloquer chez le fournisseur
  d'identité.
- Tenir un journal des demandes et des actions.

## 7. Réputation des adresses IP de sortie

Les workers contactent de nombreux domaines, dont des sites malveillants : leur IP de sortie peut être
signalée.

- Utiliser des IP de sortie dédiées aux workers, distinctes de celles du site et des e-mails.
- Surveiller ces IP sur les listes de réputation (Spamhaus, AbuseIPDB…) _[outil à choisir]_.
- La politesse est déjà assurée par l'application : 64 vérifications simultanées au plus par instance,
  4 connexions par hôte, User-Agent identifiable avec contact.
