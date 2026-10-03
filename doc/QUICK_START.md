# JTypoSquatting - Guide de Démarrage Rapide

**Version :** 2.0-alpha1  
**Temps avant premier lancement :** ~5 minutes

> [!IMPORTANT]
> **JTypoSquatting est une application à DEUX processus distincts.**
> Le backend (serveur API Spring Boot) et le frontend (interface Swing) doivent être démarrés séparément,
> dans deux terminaux différents, dans l'ordre indiqué ci-dessous.

---

## Prérequis

- **Java 21 ou supérieur** installé
  - Vérifier : `java -version`
  - Télécharger : https://www.oracle.com/java/technologies/downloads/

---

## Démarrage en 2 étapes (obligatoires)

### Étape 1 — Démarrer le Backend (Terminal 1)

Le backend est le serveur API REST/SSE qui effectue les analyses de domaines.
Il doit être démarré **en premier** et doit rester actif pendant toute la session.

```bash
# Depuis la racine du projet
./gradlew :backend:bootRun
```

Attendez le message confirmant le démarrage :
```
Started JTypoSquatting in X.XXX seconds (process running as PID XXXXX)
Tomcat started on port 8080
```

> **Alternative (script tout-en-un) :**
> ```bash
> ./scripts/run.sh
> ```

---

### Étape 2 — Lancer le Frontend (Terminal 2)

Ouvrez un **second terminal** et lancez l'interface graphique :

```bash
java -jar JTypoSquatting.jar
```

> **Depuis les sources :**
> ```bash
> ./gradlew :frontend:run
> ```

L'interface Swing s'ouvre. Si le backend est bien démarré, la barre d'état affiche :
```
✅ Connecté à http://localhost:8080
```

Si vous voyez un message d'erreur, vérifiez que l'Étape 1 est bien effectuée.

---

## Build depuis les sources

```bash
# 1. Cloner
git clone https://github.com/hernic/JTypoSquatting.git
cd JTypoSquatting

# 2. Compiler et packager
./gradlew clean build :frontend:copyFatJar

# 3. Terminal 1 — Backend
./gradlew :backend:bootRun

# 4. Terminal 2 — Frontend
java -jar JTypoSquatting.jar
```

---

## Première analyse de domaine

1. **Saisir le domaine** (ex. `www.google.com`) dans le champ en haut
2. Cliquer sur **Générer**
3. Regarder les résultats s'afficher en temps réel :
   ```
   Générés : 150 | Suspects : 45 | Inaccessibles : 105
   ```
4. **Double-cliquer** sur un domaine pour voir les détails (screenshot, headers HTTP, métadonnées)

---

## Résolution des problèmes courants

### "Impossible de se connecter au serveur backend"

Le backend n'est pas démarré ou pas encore prêt.

```bash
# Terminal 1 : démarrer le backend
./gradlew :backend:bootRun
# Attendre le message "Started on port 8080"
# Puis relancer le frontend dans Terminal 2
```

### "Java not found" / "Commande introuvable"

Java 21+ n'est pas installé.

```bash
# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Vérification
java -version
```

### Pas de screenshots / Fenêtre JavaFX introuvable

- Vérifier que le JDK 21 inclut JavaFX (ou ajouter `--module-path` JavaFX)
- Consulter les logs backend dans l'onglet **Backend** de l'interface

---

## Référence rapide

```bash
# Démarrer le backend
./gradlew :backend:bootRun

# Lancer le frontend
java -jar JTypoSquatting.jar

# Build complet
./gradlew clean build :frontend:copyFatJar

# Tests unitaires (sans tests réseau)
./gradlew test -Pexclude.network.tests=true
```

---

*Pour la documentation complète, voir le répertoire [doc/](.).*
