# search-engine
A web and mobile search engine designed to index, rank, and recommend 1,664+ Gutenberg books using advanced string-matching (KMP/RegEx) and graph centrality algorithms (Jaccard graph, PageRank/Closeness).

---

# Phase 2 — Couche data et acquisition Gutenberg

Statut : **implémentée**.

## Tables

Flyway crée désormais :

- `books`
- `terms`
- `book_terms`
- `gutenberg_import_checkpoint`

Hibernate est configuré en `ddl-auto: validate` : le schéma est versionné par les migrations SQL et Hibernate vérifie simplement qu'il correspond aux entités.

## Stockage local des livres

Les fichiers texte valides sont enregistrés dans :

```text
backend/data/books/<gutenbergId>.txt
```

Le dossier `data/` est ignoré par Git.

Chaque fichier est :

1. téléchargé ;
2. décompressé ;
3. nettoyé du boilerplate Gutenberg quand les marqueurs sont présents ;
4. compté avec un compteur de mots Unicode ;
5. conservé uniquement s'il contient au moins `10 000` mots ;
6. sauvegardé en UTF-8 ;
7. associé à un SHA-256 et à ses métadonnées en base.

## Pourquoi l'import massif ne passe pas par RapidAPI

Le plan Basic RapidAPI est limité. Télécharger le texte complet de 1 664 livres avec un appel `/api/books/{id}/text` par livre dépasserait la capacité mensuelle du plan.

L'import massif utilise donc le mécanisme officiel Project Gutenberg prévu pour les robots :

```text
https://www.gutenberg.org/robot/harvest?filetypes[]=txt&langs[]=en
```

Les fichiers sont servis depuis les miroirs Gutenberg. L'importeur respecte un délai configurable entre les téléchargements et déduplique les variantes d'encodage ayant le même ID Gutenberg.

## Endpoints d'import

### État de l'import

```http
GET /api/admin/gutenberg/import/status
```

### Importer un petit lot

```http
POST /api/admin/gutenberg/import/batch?target=1664&maxDownloads=10
```

`target` représente le nombre total de livres valides souhaité en base.

`maxDownloads` limite le nombre de nouvelles archives téléchargées pendant cet appel. Il est volontairement limité à 100.

L'import est reprenable grâce à `gutenberg_import_checkpoint`.

Exemple :

```bash
curl -X POST "http://localhost:8080/api/admin/gutenberg/import/batch?target=1664&maxDownloads=10"
```

Puis :

```bash
curl "http://localhost:8080/api/admin/gutenberg/import/status"
```

## RapidAPI Gutenberg

La clé reste exclusivement dans une variable d'environnement :

```bash
export GUTENBERG_API_KEY='...'
```

Une sonde volontaire permet de tester la connexion et de récupérer les headers de quota :

```http
POST /api/admin/gutenberg/rapidapi/probe?page=1&pageSize=10
```

Cette route **consomme une requête RapidAPI**.

La dernière information de quota observée est disponible via :

```http
GET /api/admin/gutenberg/rapidapi/quota
```

Le backend garde une réserve configurable de requêtes et bloque les nouveaux appels RapidAPI quand la limite restante devient trop faible.

## Livres importés

```http
GET /api/books?page=0&size=20
```

ne retourne que les livres ayant le statut `READY`.

```http
GET /api/books/{id}
```

retourne les métadonnées publiques d'un livre importé.

## Configuration

```yaml
app:
  storage:
    books-root: ${BOOK_STORAGE_ROOT:./data/books}

  gutenberg:
    minimum-word-count: ${GUTENBERG_MIN_WORDS:10000}
    download-delay-ms: ${GUTENBERG_DOWNLOAD_DELAY_MS:2000}
    max-uncompressed-bytes: ${GUTENBERG_MAX_UNCOMPRESSED_BYTES:25000000}
```

La prochaine étape est l'indexation de chaque fichier `READY` pour alimenter `terms` et `book_terms`.
