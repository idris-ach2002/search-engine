# DAAR — Search Engine (corpus local)

Moteur de recherche de livres avec Spring Boot, PostgreSQL et React. **PostgreSQL et le backend se lancent avec Docker Compose**, les livres sont téléchargés avec **`scripts/fast_dl.py` dans un environnement virtuel Python**, et le frontend se lance localement avec npm.

Le backend ne télécharge aucun livre. Il lit les fichiers UTF-8 de `backend/data/books/`, génère leurs métadonnées dans `backend/data/catalog.csv`, puis les importe en base et construit l'index de recherche.

## Prérequis

- Docker avec la commande `docker compose`, et le moteur Docker démarré.
- Python 3 avec `venv` et pip.
- Node.js **22.22.0 minimum** et npm, conformément aux dépendances du frontend verrouillées dans `front/package-lock.json`.
- Une connexion Internet pour installer les dépendances, construire les images et télécharger les livres.

Les commandes ci-dessous se lancent depuis la **racine du dépôt**, sauf indication contraire. Java, Maven et PostgreSQL sont fournis par les images Docker pour le lancement de l'application.

## 1. Télécharger les livres dans un environnement Python

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install datasets
python scripts/fast_dl.py
deactivate
```

Sous Windows PowerShell, remplacer la commande d'activation par `.\.venv\Scripts\Activate.ps1`.

Le script utilise `datasets` pour lire le jeu de données Hugging Face `AdhyanshVerma/pg-en` en streaming. Il vise **1 664 livres d'au moins 10 000 mots**, enregistrés sous `backend/data/books/book_0001.txt`, `book_0002.txt`, etc. La cible est définie par `target = 1664` dans le script ; il n'y a pas d'option `--target`.

Cette étape peut être sautée si les TXT sont déjà présents. **Une nouvelle exécution recommence à `book_0001.txt` et écrase les fichiers de même nom** : le script n'a pas de mécanisme de reprise. Attendre la fin du téléchargement avant de synchroniser le catalogue. Il sauvegarde uniquement le texte ; Spring Boot extrait les métadonnées ensuite.

## 2. Lancer PostgreSQL et le backend avec Docker Compose

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

Attendre le démarrage de Spring Boot, puis quitter le suivi des logs avec `Ctrl+C` ; les conteneurs continuent de tourner. Vérifier l'API :

```bash
curl 'http://localhost:8080/api/health'
```

Le fichier `docker-compose.yml` fournit deux services :

| Service | Rôle | Accès |
| --- | --- | --- |
| `postgres` | PostgreSQL 16 et stockage persistant dans `postgres_data` | Réseau Docker interne, port 5432 non publié sur l'hôte |
| `backend` | API Spring Boot, démarrée lorsque PostgreSQL est prêt | `http://localhost:8080` |

Le dossier local `./backend/data` est monté dans `/app/data` du backend. Le CSV généré par le conteneur est donc visible dans le dépôt. Les migrations Flyway sont appliquées au démarrage.

Les valeurs de développement sont définies dans Compose. Pour les personnaliser, créer un fichier `.env` à la racine avec `DB_NAME`, `DB_USER` et `DB_PASSWORD` ; ce fichier est distinct de l'environnement Python `.venv`. Voir la [configuration backend](docs/BACKEND.md#15-configuration).

## 3. Synchroniser, indexer et calculer les recommandations

Une fois le téléchargement terminé et l'API démarrée, exécuter dans cet ordre :

```bash
# Générer/enrichir catalog.csv et importer les livres dans PostgreSQL.
curl -X POST 'http://localhost:8080/api/admin/catalog/sync'

# Vérifier le catalogue généré et les textes locaux.
python3 scripts/verify_local_corpus.py --target 1664

# Indexer les livres en attente.
curl -X POST 'http://localhost:8080/api/admin/indexing/all?maxBooks=2000'
curl 'http://localhost:8080/api/admin/indexing/status'

# Lorsque unindexedBooks vaut 0, construire Jaccard puis PageRank.
curl -X POST 'http://localhost:8080/api/admin/similarity/rebuild'
curl -X POST 'http://localhost:8080/api/admin/ranking/pagerank/rebuild'

# Tester une recherche.
curl 'http://localhost:8080/api/search?q=man&page=0&size=20'
```

Relancer l'indexation si des livres restent en attente et vérifier les éventuels échecs dans la réponse. La synchronisation, l'indexation et les calculs de graphe ne sont pas déclenchés par `docker compose up`.

Pour retirer aussi de PostgreSQL les livres absents du dossier local, utiliser `/api/admin/catalog/sync?prune=true`. Le bouton **Synchroniser le catalogue** du monitoring utilise cette option.

## 4. Lancer le frontend avec npm

Dans un autre terminal, depuis la racine :

```bash
cd front
npm install
npm run build
npm run dev
```

Ouvrir l'adresse affichée par Vite, généralement **`http://localhost:5173`**. L'installation des dépendances se fait avec **`npm install`**. `npm run build` produit la version compilée ; `npm run dev` démarre le serveur de développement.

Le frontend appelle `http://localhost:8080` par défaut. Pour une autre adresse, définir `VITE_API_BASE_URL` au lancement ou avant le build. Pour servir le build déjà produit, utiliser `npm run start` à la place de `npm run dev`. Voir le [README du frontend](front/README.md).

## Utilisation quotidienne

```bash
# Depuis la racine : démarrer les services existants.
docker compose up -d

# Après une modification du code Java : reconstruire le backend.
docker compose up -d --build backend

# Consulter les logs.
docker compose logs -f backend

# Arrêter les services en conservant les données PostgreSQL.
docker compose down
```

Arrêter le serveur frontend avec `Ctrl+C`. L'option `docker compose down -v` supprime le volume PostgreSQL ; elle n'est pas nécessaire pour un arrêt normal. Après un ajout ou une modification des TXT, reprendre la synchronisation puis l'indexation, Jaccard et PageRank.

## Organisation et métadonnées

```text
backend/
  data/
    books/book_0001.txt      # textes téléchargés par fast_dl.py
    catalog.csv              # généré/enrichi par Spring Boot
  Dockerfile                 # construction Maven puis exécution Java 21
front/
  package.json               # dépendances et scripts build/dev/start
scripts/
  fast_dl.py                 # téléchargement avec la bibliothèque datasets
  verify_local_corpus.py     # vérification après génération du CSV
docker-compose.yml          # PostgreSQL + backend
```

Le CSV contient `storage_key,title,author,language,source_url`. Les valeurs existantes restent prioritaires ; les champs vides et les titres de secours sont complétés depuis les en-têtes ou la page de titre du TXT. La langue peut être estimée localement. Une URL source nécessite une URL explicite ou une référence Gutenberg identifiable : les informations absentes ou ambiguës restent vides, avec un titre de secours si nécessaire.

Les scripts `download_sample_books.py` et `download_books_rsync.py` restent disponibles comme alternatives ; ils ne font pas partie de la procédure courante avec `fast_dl.py`.

La [référence backend](docs/BACKEND.md) détaille le catalogue, les migrations, les routes HTTP, les algorithmes et les tests. Le [README frontend](front/README.md) décrit les commandes npm et les vues de l'application.

Les routes `/api/admin/**` nécessitent une protection avant une exposition publique. Les textes téléchargés ne sont pas automatiquement libres de droit dans tous les pays.
