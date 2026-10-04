# Rapport d'Audit de Code Approfondi — JTypoSquatting

**Projet :** JTypoSquatting  
**Version analysée :** 2.0-alpha1  
**Date d'audit :** Septembre 2026  
**Périmètre :** Modules `backend`, `frontend`, `shared`, configuration Gradle et scripts d'exécution  
**Statut :** Recommandations d'Amélioration & Plan de Remédiation  

---

## 1. Synthèse Exécutive et Tableau de Bord

L'audit approfondi de la base de code de JTypoSquatting révèle un projet reposant sur des fondations technologiques modernes (Java 21, Spring Boot 3.5, Virtual Threads, interface Swing réactive avec SSE), mais souffrant d'anomalies structurelles critiques dans les domaines de la **concurrence**, de la **sécurité**, de la **fiabilité opérationnelle** et de la **qualité des tests**.

Plusieurs composants présentent des bugs bloquants silencieux (ex: le lecteur de logs `LogTailer` arrêté immédiatement à l'initialisation, des compteurs d'état bloqués à zéro, une perte de 90% des variantes de fautes d'orthographe, et une absence de thread-safety sur la connexion JDBC entraînant des corruptions potentielles de base de données).

### Grille d'Évaluation Globale

| Dimension | Note | Statut | Synthèse |
|---|:---:|:---:|---|
| **Architecture Globale** | `13/20` | ⚠️ À consolider | Bonne séparation modulaire en théorie, mais couplages masqués et discordance de démarrage. |
| **Sécurité Applicative** | `08/20` | 🔴 Critique | Bypass SSL total (TrustAll), navigation directe sur domaines malveillants, CORS permissif. |
| **Concurrence & Threads** | `07/20` | 🔴 Critique | JDBC mono-connexion sur Virtual Threads, SseEmitter non synchronisé, gel JavaFX de 8s. |
| **Fiabilité & Robustesse** | `10/20` | 🟠 Insuffisant | Bugs silencieux dans `LogTailer`, `KeysValuesSwap`, parsing de domaines invalides avec tirets. |
| **Maintenabilité & Code** | `11/20` | 🟡 Moyen | Code mort (17 algorithmes fantômes, DTO inactif), mélange json-simple/gson, `System.out`. |
| **Qualité des Tests** | `06/20` | 🔴 Critique | Aucun test frontend/shared, faux tests unitaires backend dépendants du réseau et de X11. |

---

## 2. Vulnérabilités de Sécurité Critiques

### SEC-01 : Désactivation globale et aveugle de la vérification TLS/SSL (TrustAll)
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/service/PageAnalyzer.java` (lignes 136-157)
- **Gravité :** 🔴 **Élevée** (CWE-295: Improper Certificate Validation)
- **Constat :**
  Pour interroger les domaines cibles en HTTPS, `PageAnalyzer` écrase le gestionnaire de confiance SSL et le vérificateur de nom d'hôte avec des implémentations permissives qui acceptent n'importe quel certificat invalide, expiré ou auto-signé :
  ```java
  httpsConn.setHostnameVerifier((hostname, session) -> true);
  SSLContext sc = SSLContext.getInstance("TLS");
  sc.init(null, new TrustManager[]{
      new X509TrustManager() {
          public X509Certificate[] getAcceptedIssuers() { return null; }
          public void checkClientTrusted(X509Certificate[] certs, String authType) {}
          public void checkServerTrusted(X509Certificate[] certs, String authType) {}
      }
  }, new SecureRandom());
  httpsConn.setSSLSocketFactory(sc.getSocketFactory());
  ```
- **Impact :** Bien que l'intention soit d'analyser des domaines squatters pouvant avoir des certificats mal configurés, cette méthode désactive la sécurité cryptographique au niveau de la connexion et rend l'outil vulnérable aux attaques par interception (Man-in-the-Middle). De plus, elle utilise `SecureRandom` à chaque requête de manière sous-optimale.
- **Remédiation :** Encapsuler l'inspection SSL dans un client HTTP dédié avec enregistrement du statut du certificat (valide / auto-signé / invalide) sans masquer l'information, ou paramétrer un `SSLContext` contrôlé sans supprimer la vérification globale.

---

### SEC-02 : Lancement direct de domaines malveillants non vérifiés dans le navigateur de l'hôte
- **Localisation :** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/ui/DomainDetailsDialog.java` (lignes 67-74)
- **Gravité :** 🔴 **Élevée** (CWE-601: URL Redirection to Untrusted Site)
- **Constat :**
  Dans la boîte de dialogue de détails, le bouton « Open in Browser » exécute directement :
  ```java
  openBtn.addActionListener(e -> {
      try {
          String urlStr = data.getDomain().startsWith("http") ? data.getDomain() : "https://" + data.getDomain();
          if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(urlStr));
      } catch (Exception ex) { ... }
  });
  ```
- **Impact :** JTypoSquatting a précisément pour rôle de détecter des domaines de phishing, d'escroquerie ou infectés par des malwares. Un analyste cliquant sur ce bouton ouvre le site frauduleux dans sa session de navigation personnelle par défaut, exposant son poste à des attaques par navigateur (drive-by download, vol de session, exploitation zero-day).
- **Remédiation :** Ajouter une boîte d'avertissement explicite (Confirmation Dialog avec indication de risque d'exposition malveillante) avant ouverture, ou recommander l'ouverture dans un bac à sable (sandbox / conteneurisé).

---

### SEC-03 : Endpoints destructeurs ouverts sans restriction de contrôle
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/controller/TypoSquattingController.java` (lignes 161-183) et `DomainDataController.java` (lignes 138-146)
- **Gravité :** 🟠 **Moyenne** (CWE-306: Missing Authentication for Critical Function)
- **Constat :**
  Les endpoints `DELETE /api/cancel-and-clear` et `DELETE /api/data/all` suppriment immédiatement toutes les données de la base H2 sans aucun token, ni validation de session.
- **Impact :** Tout processus ou script exécuté sur le réseau local ou via un navigateur via une requête forgée cross-origin peut purger instantanément les résultats d'analyses en cours.

---

### SEC-04 : Configuration CORS permissive Wildcard
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/config/WebConfig.java` (lignes 17-23)
- **Gravité :** 🟡 **Faible à Moyenne**
- **Constat :**
  `registry.addMapping("/api/**").allowedOrigins("*")` autorise n'importe quelle page web externe à interroger l'API locale sur le port 8080.
- **Remédiation :** Restreindre aux origines légitimes ou aux requêtes locales `http://localhost:*`, `http://127.0.0.1:*`.

---

## 3. Anomalies Majeures de Concurrence & Thread-Safety

### CONC-01 : Utilisation d'une instance unique de `java.sql.Connection` partagée entre Virtual Threads
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/db/DatabaseService.java` (lignes 18, 33, 76-98)
- **Gravité :** 🔴 **Critique** (CWE-388 / Concurrency Issue)
- **Constat :**
  La classe `@Component DatabaseService` ouvre **une seule et unique connexion JDBC** lors de son `@PostConstruct` :
  ```java
  private Connection connection;
  ...
  connection = DriverManager.getConnection(datasourceUrl, "sa", "");
  ```
  Cette même référence `connection` est ensuite appelée concurremment dans `save()` par des dizaines de Virtual Threads exécutant `checkDomain()` en parallèle :
  ```java
  try (PreparedStatement ps = connection.prepareStatement(sql)) { ... ps.executeUpdate(); }
  ```
- **Impact :** La spécification JDBC stipule expressément que `java.sql.Connection` **n'est pas thread-safe**. Plusieurs threads exécutant simultanément des requêtes sur la même connexion s'entrecroisent, perturbent les états internes des transactions, génèrent des erreurs `SQLException: object closed` ou corrompent les données H2.
- **Remédiation :** Remplacer immédiatement `DriverManager.getConnection()` par un pool de connexions managé par Spring Boot (`HikariCP` avec `spring-boot-starter-jdbc` ou `JdbcTemplate`). Chaque requête doit emprunter et restituer sa propre connexion au pool.

---

### CONC-02 : `SseEmitter` non synchronisé appelé concurremment par des Virtual Threads
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/controller/TypoSquattingController.java` (lignes 91-105, 185-194)
- **Gravité :** 🔴 **Critique**
- **Constat :**
  Dans `generateAndCheckDomains()`, une boucle soumet chaque vérification de domaine à l'exécuteur virtuel :
  ```java
  sessionExecutor.submit(() -> {
      DomainResultDTO finalResult = domainCheckService.checkDomain(generatedDomain);
      if (finalResult != null) {
          sendSseEvent(emitter, finalResult); // Non synchronisé !
      }
  });
  ```
  Or, dans Spring MVC, `SseEmitter.send()` n'est **pas thread-safe**.
- **Impact :** Deux Virtual Threads terminant leur analyse au même instant invoquent `emitter.send()` en même temps. Cela provoque des exceptions `IllegalStateException: ResponseBodyEmitter has already been marked as complete` ou produit un flux HTTP SSE entrelacé et corrompu, provoquant des erreurs de parsing JSON côté frontend.
- **Remédiation :** Synchroniser les appels d'émission SSE sur un verrou dédié ou utiliser une file d'attente d'événements dédiée pour l'émetteur :
  ```java
  private void sendSseEvent(SseEmitter emitter, Object data) throws IOException {
      synchronized (emitter) {
          emitter.send(SseEmitter.event()
              .name("domainUpdate")
              .data(data, MediaType.APPLICATION_JSON)
              .id(String.valueOf(System.currentTimeMillis())));
      }
  }
  ```

---

### CONC-03 : Collections non synchronisées partagées entre l'EDT et des Threads de fond
- **Localisation :** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/ui/DomainStreamingService.java` (lignes 28-29, 38, 88-124, 155-172)
- **Gravité :** 🟠 **Élevée**
- **Constat :**
  Les champs suivants sont déclarés comme de simples `HashMap` :
  ```java
  private final Map<String, Integer> domainRowMap = new HashMap<>();
  private final Map<String, Long> testingDomainTimestamps = new HashMap<>();
  private final Map<String, Boolean> processingState = new HashMap<>();
  ```
  Cependant :
  - `drain` les modifie sur l'Event Dispatch Thread (EDT) de Swing (`SwingUtilities.invokeLater`).
  - `scheduleTimeoutCheck` les lit et les modifie depuis le pool `timeoutExecutor` (Thread `TimeoutChecker`).
  - `startDomainChecks` et `reset` les vident depuis `executorService`.
- **Impact :** Accès concurrent sans synchronisation sur `HashMap` standard, risquant des corruptions de pointeurs internes (boucle infinie sur `HashMap.get()` ou lectures sales).
- **Remédiation :** Remplacer ces structures par `ConcurrentHashMap` ou synchroniser strictement tous leurs accès.

---

### CONC-04 : Condition de course critique et réinitialisation intempestive de `TLD_LIST`
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/generator/TLD.java` (lignes 18, 87-90) et `JTypoSquatting.java` (lignes 31-35)
- **Gravité :** 🟠 **Élevée**
- **Constat :**
  La variable `TLD_LIST` est un `HashSet` statique non thread-safe.
  À chaque instanciation de `new JTypoSquatting(domain)`, le constructeur invoque :
  ```java
  TLD.UpdateTLDList();
  ```
  Qui fait :
  ```java
  public static void UpdateTLDList() throws IOException {
      TLD_LIST.clear();
      loadTLDList();
  }
  ```
- **Impact :** Si deux analyses s'exécutent simultanément ou si une requête arrive pendant qu'une autre termine, `TLD_LIST.clear()` vide la liste en plein vol alors qu'un autre thread est en train d'itérer dessus dans `AddAndReplaceAllTLD()`. Cela lève immédiatement une `ConcurrentModificationException` ou génère 0 domaine TLD. De plus, relire le fichier `TLD.txt` depuis le disque à chaque saisie utilisateur est une aberration pour les performances.
- **Remédiation :** Charger `TLD_LIST` une seule fois de manière immuable (`Collections.unmodifiableSet`) au démarrage de l'application et supprimer l'appel systématique dans le constructeur de `JTypoSquatting`.

---

### CONC-05 : Gel sévère du thread JavaFX Application Thread par des `sleep()` bloquants
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/service/PageAnalyzer.java` (lignes 321-329, 341-357, 398-406)
- **Gravité :** 🔴 **Critique**
- **Constat :**
  Dans `captureScreenshot()`, du code s'exécute sur le thread d'interface JavaFX via `Platform.runLater()` :
  ```java
  webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
      if (newState == Worker.State.SUCCEEDED) {
          // Attente active bloquante sur le JavaFX Thread !
          for (int i = 0; i < 8; i++) {
              sleep(1000); // 8 SECONDES DE SLEEP SUR LE THREAD JAVAFX !
          }
          ...
      }
  });
  ```
- **Impact :** `Platform.runLater` et les écouteurs de `Worker` s'exécutent sur l'unique **JavaFX Application Thread**. En effectuant un `Thread.sleep(1000)` répété 8 fois, le thread graphique de JavaFX est totalement gelé pendant 8 secondes par capture ! Aucun autre événement de rendu, aucun autre snapshot ni aucun chargement de page ne peut être traité durant ce temps. La capture de 20 domaines concurrents est ainsi sérialisée et prend plus de 160 secondes de blocage total du moteur graphique.
- **Remédiation :** Utiliser des transitions asynchrones ou un `PauseTransition` JavaFX non-bloquant (`PauseTransition pause = new PauseTransition(Duration.seconds(2)); pause.setOnFinished(e -> takeSnapshot()); pause.play();`).

---

## 4. Bugs Fonctionnels et Régressions Identifiés

### BUG-01 : Destruction immédiate de `LogTailer` à l'initialisation (try-with-resources AutoCloseable)
- **Localisation :** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/ui/LogTailer.java` (lignes 57-62, 117-119)
- **Gravité :** 🔴 **Bloquant pour la fonctionnalité de logs**
- **Constat :**
  La méthode `start()` déclare :
  ```java
  try (ScheduledExecutorService logExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> ...)) {
      future = logExecutor.scheduleWithFixedDelay(() -> {
          ...
      }, 1000, 1000, TimeUnit.MILLISECONDS);
  }
  ```
  Depuis Java 19, `ExecutorService` implémente l'interface `AutoCloseable`.
- **Impact :** Dès que la méthode `start()` atteint la fin du bloc `try`, Java invoque automatiquement `logExecutor.close()`, qui déclenche `shutdown()` et attend l'arrêt du pool ! L'exécuteur est donc détruit une fraction de seconde après son lancement. **Le tailer de logs ne s'exécute jamais en tâche de fond**.
- **Remédiation :** Conserver `ScheduledExecutorService` comme variable membre de la classe `LogTailer`, ne pas utiliser de `try-with-resources`, et le fermer explicitement dans la méthode `stop()`.

---

### BUG-02 : Incohérence des libellés de statut et compteurs d'état gelés à zéro
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/service/DomainCheckService.java` (ligne 45) vs `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/ui/DomainStreamingService.java` (lignes 112-113)
- **Gravité :** 🟠 **Moyenne**
- **Constat :**
  Côté Backend, `DomainCheckService` assigne comme statut :
  ```java
  String status = (pageData.getHttpCode() == 200) ? "Suspicious" : "Safe";
  // ou "Unreachable"
  ```
  Mais côté Frontend, `DomainStreamingService` évalue :
  ```java
  if ("Active".equals(result.getStatus())) activeCount.incrementAndGet();
  else if ("Dead".equals(result.getStatus())) deadCount.incrementAndGet();
  ```
- **Impact :** Les statuts `"Active"` et `"Dead"` ne sont jamais émis par le backend ! En conséquence, `activeCount` et `deadCount` restent perpétuellement à **0** dans le service de streaming, faussant toutes les statistiques affichées.
- **Remédiation :** Harmoniser les constantes de statut dans une énumération partagée `DomainStatus` (`SUSPICIOUS`, `SAFE`, `TESTING`, `UNREACHABLE`).

---

### BUG-03 : Perte massive de variantes orthographiques dans `KeysValuesSwap`
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/util/JSonUtils.java` (lignes 15-34)
- **Gravité :** 🟠 **Moyenne (Perte de couverture algorithmique)**
- **Constat :**
  Le fichier `common-misspellings.json` contient des paires `"faute": "mot_correct"`.
  Pour inverser le dictionnaire, `KeysValuesSwap` insère dans un `JSONObject` :
  ```java
  String token = tokenizer.nextToken().strip();
  Object put = joOut.put(token, keyIn);
  ```
- **Impact :** Si le mot correct possède plusieurs fautes fréquentes différentes (ce qui est le cas pour des centaines de mots, ex: `"absence"` qui a `"abscence"`, `"absense"`, `"absance"`), chaque appel `joOut.put("absence", ...)` **écrase** la faute précédente ! Le dictionnaire ne conserve qu'une seule variante par mot et en détruit des centaines.
- **Remédiation :** Utiliser une structure multi-valeurs : `Map<String, List<String>>`.

---

### BUG-04 : Génération de noms de domaine invalides commençant par un point
- **Localisation :** `backend/src/main/java/com/aleph/graymatter/jtyposquatting/generator/Dash.java` (lignes 21, 47)
- **Gravité :** 🟠 **Moyenne**
- **Constat :**
  Dans `Dash.java`, la recomposition du domaine utilise :
  ```java
  String subDomain = domainName.getSubDomain();
  String tld = domainName.getTLD();
  ...
  String newDomain = subDomain + '.' + sb + '.' + tld;
  ```
  Si l'utilisateur a saisi `example.com` (sans sous-domaine `www`), `domainName.getSubDomain()` renvoie `""`.
- **Impact :** `newDomain` devient `"" + '.' + "ex-ample" + '.' + "com"` = **`.ex-ample.com`**.
  Le domaine généré commence par un point illégal !
- **Remédiation :** Tester la présence du sous-domaine avant d'insérer le point :
  ```java
  String newDomain = (subDomain.isEmpty() ? "" : subDomain + ".") + sb + "." + tld;
  ```

---

### BUG-05 : Validation syntaxique défaillante dans `DomainName`
- **Localisation :** `shared/src/main/java/com/aleph/graymatter/jtyposquatting/net/DomainName.java` (lignes 33-35)
- **Gravité :** 🟡 **Faible à Moyenne**
- **Constat :**
  La méthode de validation est la suivante :
  ```java
  private static boolean isValidDomain(String domainName) {
      return domainName.contains(".") && domainName.indexOf('.') != domainName.length() - 1;
  }
  ```
- **Impact :** Des chaînes totalement aberrantes comme `" . "`, `"http://a.b"`, `"foo..bar"`, `"127.0.0.1"` ou `"-abc.com"` sont considérées comme des domaines valides, provoquant des crashs plus bas dans les générateurs ou des requêtes HTTP invalides.
- **Remédiation :** Implémenter une validation RFC 1035 / RFC 1123 basée sur une Regex conforme ou la classe `InternetDomainName` de Google Guava (déjà présente dans les dépendances du projet !).

---

### BUG-06 : Méthode `ConfigManager.setLocale()` inopérante
- **Localisation :** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/config/ConfigManager.java` (lignes 96-98)
- **Gravité :** 🟡 **Faible**
- **Constat :**
  ```java
  public void setLocale(Locale locale) {
      ResourceBundle.clearCache();
  }
  ```
- **Impact :** Vider le cache ne change ni la locale par défaut de la JVM, ni la référence du `ResourceBundle messages` déjà instancié dans le constructeur. Changer la langue dans l'UI n'a strictement aucun effet.

---

### BUG-07 : `ClientConfig` vulnérable à un `NullPointerException`
- **Localisation :** `frontend/src/main/java/com/aleph/graymatter/jtyposquatting/config/ClientConfig.java` (lignes 23-28)
- **Gravité :** 🟡 **Faible**
- **Constat :**
  ```java
  props.load(ClientConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE));
  ```
  Si `client.properties` n'est pas présent sur le classpath, `getResourceAsStream()` retourne `null`. `props.load(null)` lève un `NullPointerException` non capturé par le bloc `catch (IOException e)`.

---

## 5. Dette Technique et Écarts Architecturaux

### DEBT-01 : 17 algorithmes annoncés mais non implémentés
- **Constat :** `Const.ALGO_NAME_LIST` énumère fièrement 21 algorithmes de typo-squatting (`omission`, `repetition`, `changeOrder`, `vowelSwap`, `homophones`, `singularPluralize`, `numeralSwap`, etc.) ainsi qu'une table de chiffres `NUMERAL`. En réalité, **seuls 4 algorithmes** (`Dash`, `Homoglyph`, `Misspell`, `TLD`) existent dans le projet. Les 17 autres ne sont que des chaînes de caractères déclaratives orphelines.

### DEBT-02 : Code mort et DTOs inutilisés
- `GenerationRequestDTO.java` : Déclaré dans le module `shared`, il ne possède ni constructeur personnalisé, ni getters/setters, et n'est injecté dans aucune méthode du projet.
- `TypoSquattingController.java` (ligne 41) : `private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();` est instancié mais jamais référencé.
- `DatabaseService.java` (ligne 19) : `private Server h2Server;` initialisé à `null` et inutilisé.

### DEBT-03 : Coexistence de bibliothèques JSON redondantes
- Le projet intègre simultanément `com.googlecode.json-simple:json-simple:1.1.1` (bibliothèque obsolète de 2012 sans génériques) et `com.google.code.gson:gson:2.10.1`, tout en utilisant Jackson sous le capot avec Spring Boot. Cette redondance alourdit le binaire et complexifie la sérialisation (ex: `@SerializedName` et `transient` pour gérer Gson et Jackson en parallèle).

### DEBT-04 : Paramètres de configuration fantômes pour Tomcat
- `backend/src/main/resources/application-production.properties` configure `server.tomcat.threads.max=200` et `server.tomcat.threads.min-spare=10`. Cependant, `build.gradle` remplace Tomcat par Jetty (`spring-boot-starter-jetty`). Ces propriétés sont donc totalement ignorées. De même, les métriques `management.endpoints...` sont configurées alors que `spring-boot-starter-actuator` n'est pas inclus.

### DEBT-05 : Journalisation hétérogène et verbeuse
- Présence de nombreux `System.out.println(...)` et `System.err.println(...)` dans `DomainCheckService.java`, `PreviewPanel.java`, etc., au lieu d'utiliser systématiquement l'infrastructure SLF4J (`LoggerFactory.getLogger(...)`).

---

## 6. Évaluation de la Couverture et Stratégie de Test

### TEST-01 : Absence intégrale de tests unitaires sur `frontend` et `shared`
- Les modules `frontend` et `shared` ne comportent **aucun fichier de test**. Les générateurs d'algorithmes, les classes utilitaires et le moteur de streaming ne font l'objet d'aucune validation automatisée.

### TEST-02 : Faux tests unitaires avec dépendances externes dans le `backend`
- La classe `PageAnalyzerUnitTest.java` porte le suffixe `UnitTest`, mais exécute de vraies requêtes HTTP réseau vers `www.aleph-networks.eu` et instancie la stack graphique JavaFX.
- **Conséquences :**
  - Si la machine de build n'a pas accès à Internet, les tests échouent.
  - Si le site distant est lent ou indisponible, le build est bloqué.
  - Si aucun serveur X11 n'est présent (environnement CI headless standard sans Xvfb), le build freeze pendant 30 secondes par test avant de crasher.
- **Remédiation :** Utiliser des mocks (`Mockito`) et un serveur HTTP de test local (`MockWebServer` ou `WireMock`) pour tester le parsing HTML et la logique d'analyse sans aucune dépendance extérieure.

---

## 7. Plan d'Améliorations Priorisées (Matrice Impact / Effort)

```
        ▲ Impact
        │
        │  [CONC-01] Pool HikariCP        [CONC-05] Refactor JavaFX
        │  [CONC-02] Sync SSE Emitter     [SEC-01] Sécurisation SSL
        │  [BUG-01] Fix LogTailer         [TEST-02] Mocks / WireMock
        │  [BUG-03] Fix KeysValuesSwap
        │
        │  [BUG-02] Enum Status           [DEBT-01] Implémenter algos
        │  [BUG-04] Fix Dash leading dot  [DEBT-03] Supprimer json-simple
        │  [SEC-02] Warning Navigation
        │
        └────────────────────────────────────────────────────────► Effort
                 Faible / Moyen                     Élevé
```

### 7.1 Actions Immédiates (Priorité 1 — Urgentes)

1. **Remplacement de la connexion JDBC unique par HikariCP / JdbcTemplate** :
   Dans `DatabaseService.java`, injecter `javax.sql.DataSource` managé par Spring Boot et éliminer la variable d'instance `Connection`.
2. **Correction du blocage JavaFX dans `PageAnalyzer`** :
   Supprimer les `sleep(1000)` bloquants exécutés sur le JavaFX Application Thread et passer par un mécanisme asynchrone non-bloquant.
3. **Réparation de `LogTailer`** :
   Supprimer le `try-with-resources` fermant le `ScheduledExecutorService` prématurément.
4. **Synchronisation des émissions SSE** :
   Poser un verrou `synchronized (emitter)` dans `sendSseEvent` pour éviter les conflits d'écriture entre Virtual Threads.
5. **Correction de la perte de variantes dans `KeysValuesSwap`** :
   Adapter la structure pour supporter plusieurs variantes orthographiques par mot cible (`Map<String, List<String>>`).

### 7.2 Actions de Consolidation (Priorité 2 — Moyen Terme)

6. **Harmonisation des statuts de domaines** :
   Créer une énumération `DomainStatus` dans le module `shared` et synchroniser les compteurs `activeCount` et `deadCount` du frontend.
7. **Correction de `Dash.java` et `DomainName.java`** :
   Corriger la concaténation évitant les points initiaux illégaux et durcir la validation via `InternetDomainName` de Guava.
8. **Sécurisation de la consultation externe** :
   Ajouter une pop-up de confirmation avec avertissement de sécurité avant d'ouvrir un domaine dans le navigateur hôte.
9. **Remplacement des faux tests unitaires par des tests isolés (WireMock)** :
   Découpler les tests unitaires de la connexion Internet et créer une suite de tests complète pour les algorithmes du module partagé.

### 7.3 Actions d'Assainissement (Priorité 3 — Dette Technique)

10. **Suppression de `json-simple` au profit exclusif de `Gson` / `Jackson`**.
11. **Nettoyage du code mort** :
    - Supprimer `GenerationRequestDTO` ou le relier aux endpoints.
    - Supprimer les propriétés Tomcat résiduelles dans `application-production.properties`.
    - Remplacer tous les `System.out.println` par des `logger.info()` / `logger.debug()`.
12. **Mise en œuvre des 17 algorithmes manquants** pour honorer les capacités promises par l'interface et la documentation.
