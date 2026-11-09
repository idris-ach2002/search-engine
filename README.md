# DAAR — Search Engine (corpus local)

Moteur de recherche de livres, Spring Boot / PostgreSQL / React. **Le backend ne télécharge aucun livre** : les fichiers texte UTF-8 et leurs métadonnées sont fournis localement dans `backend/data/`.

Le programme de téléchargement est **un script Python séparé** : `scripts/download_sample_books.py`. Il utilise le **catalogue CSV officiel téléchargé en une fois**, puis des **miroirs Gutenberg officiels** pour obtenir les fichiers TXT, **sans RapidAPI**. L’ancienne URL `robot/harvest` répond désormais 404 et n’est plus utilisée. Son exécution est facultative : déposer des livres UTF-8 `.txt` dans `backend/data/books/` suffit ; le serveur construit automatiquement `catalog.csv`. Les métadonnées existantes sont conservées (pour les noms SHA-256, conserver le CSV reste recommandé pour afficher les vrais titres).

## Schéma des fichiers

```text
backend/
  data/
    catalog.csv              # créé ou mis à jour automatiquement à la synchronisation
    books/
      a71e0....txt           # noms opaques, indépendants des titres
      roman_quelconque.txt
  src/main/...               # code Java du serveur
scripts/
  download_sample_books.py   # approvisionnement hors-ligne du serveur
  verify_local_corpus.py     # contrôle intégral du corpus, sans réseau
```

Le CSV contient `storage_key,title,author,language,source_url` mais sa création manuelle n'est **plus nécessaire**. `POST /api/admin/catalog/generate` le crée sans modifier PostgreSQL ; `POST /api/admin/catalog/sync` le génère puis importe les livres. Métadonnées : CSV existant → en-têtes `Title:/Author:/Language:` → nom lisible du TXT. Un nom SHA-256 sans métadonnées ne permet pas de connaître le vrai titre ; l'application affiche alors `Titre inconnu [clé]`. Le contenu indexé reste celui du TXT.

## Démarrage (depuis la racine du dépôt)

```bash
# 1. Télécharger 600 livres valides. Facultatif si votre bibliothèque existe déjà.
python3 scripts/download_sample_books.py --probe
python3 scripts/download_sample_books.py --target 600

# 2. Vérifier les fichiers déjà présents, sans réseau.
python3 scripts/verify_local_corpus.py

# 3. Lancer le backend (PostgreSQL local + DB_PASSWORD configuré).
cd backend
mvn clean test
mvn spring-boot:run
```

Dans un autre terminal :

```bash
# Génère catalog.csv depuis data/books puis importe en base.
# prune=true supprime de PostgreSQL les entrées absentes du dossier.
curl -X POST 'http://localhost:8080/api/admin/catalog/sync?prune=true'

# Indexation en plusieurs lots si nécessaire.
curl -X POST 'http://localhost:8080/api/admin/indexing/all?maxBooks=2000'

# Reconstruire les données dérivées après l'index.
curl -X POST 'http://localhost:8080/api/admin/similarity/rebuild'
curl -X POST 'http://localhost:8080/api/admin/ranking/pagerank/rebuild'

# Rechercher.
curl 'http://localhost:8080/api/search?q=man&page=0&size=20'
```

### Nombre de livres

Le téléchargement cible **600** textes d'au moins **10 000 mots** par défaut, pour les essais. Pour respecter l'objectif final de l'énoncé (**1 664 livres**), utiliser `--target 1664`, puis relancer synchronisation, indexation et calculs de graphe/centralité.

### Migration RegEx Java et performances d’indexation

[**CHANGEMENTS_REGEX_INDEXATION.md**](CHANGEMENTS_REGEX_INDEXATION.md) : suppression des automates maison de la recherche avancée, optimisation des écritures PostgreSQL, mesures des étapes d’indexation et protocole de comparaison.

### Documentation exhaustive

[**docs/BACKEND.md**](docs/BACKEND.md) : architecture, catalogue automatique, sécurité, migrations V5/V6, toutes les routes HTTP, algorithmes (KMP, RegEx, Jaccard, PageRank), ranking, recommandations, erreurs, procédures et tests.

**Attention** : l'application ne doit pas être exposée à Internet telle quelle : les routes `/api/admin/**` nécessitent une protection avant déploiement. Les textes téléchargés ne sont pas automatiquement libres de droit dans tous les pays.
