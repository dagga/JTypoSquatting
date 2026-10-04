# JTypoSquatting - Guide de Démarrage Rapide

**Version :** 2.0-alpha1  
**Temps avant premier lancement :** ~5 minutes

> [!IMPORTANT]
> **JTypoSquatting est désormais une application tout-en-un.**
> Le backend et le frontend sont embarqués dans le même JAR et démarrent simultanément.

---

## Démarrage rapide

Lancez simplement l'application :

```bash
java -jar JTypoSquatting.jar
```

Si le backend a besoin de ports spécifiques ou de configuration, ils sont gérés automatiquement ou via le fichier `application.properties`.

---

## Build depuis les sources

```bash
# 1. Cloner
git clone https://github.com/hernic/JTypoSquatting.git
cd JTypoSquatting

# 2. Compiler et packager
./gradlew clean build :frontend:copyFatJar

# 3. Lancer
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
# Lancer l'application
java -jar JTypoSquatting.jar

# Build complet
./gradlew clean build :frontend:copyFatJar

# Tests unitaires (sans tests réseau)
./gradlew test -Pexclude.network.tests=true
```

---

*Pour la documentation complète, voir le répertoire [doc/](.).*
