# Référence technique — backend DAAR Search Engine (corpus local, V6)

## 1. Objectif et périmètre

Le serveur fournit la recherche simple et avancée sur des livres en texte intégral stockés **localement**. Il ne contacte **aucune API externe** et n'utilise aucun service d'importation dans Spring Boot. Les fichiers sont déposés sous `backend/data/books/` par le script **externe** `scripts/fast_dl.py`, exécuté dans un environnement virtuel Python avec la dépendance `datasets`. Spring Boot génère/enrichit ensuite `catalog.csv` lors de la synchronisation. Le dépôt se lance avec **Docker Compose pour PostgreSQL et le backend**, et **npm en local pour le frontend**.

Le script courant vise **1 664 livres** d'au moins **10 000 mots** chacun. Ce nombre est une cible de téléchargement : vérifier le résultat avec `verify_local_corpus.py --target 1664` après génération du catalogue.

Fonctions du serveur : catalogue local → contrôle des fichiers → indexation inversée → KMP/RegEx → ranking lexical/PageRank → graphe Jaccard → suggestions.

## 2. Architecture des fichiers

```text
search-engine/
├── backend/
│   ├── data/
│   │   ├── catalog.csv             (généré localement, exclu de Git)
│   │   └── books/
│   │       ├── book_0001.txt       (produit par fast_dl.py)
│   │       └── mon-livre.txt       (autorisé aussi)
│   ├── src/main/java/com/sorbonne/backend/
│   │   ├── book/                  entité et routes de consultation
│   │   ├── catalog/               lecteur CSV, vérification locale, synchronisation
│   │   ├── indexing/              normalisation, postings, administration
│   │   ├── search/                KMP/RegEx, calcul des scores lexicaux
│   │   ├── similarity/            graphe Jaccard
│   │   ├── ranking/               PageRank pondéré, ranking hybride
│   │   ├── recommendation/        suggestions multi-graines
│   │   └── health/
│   └── src/main/resources/db/migration/V1..V6
├── scripts/
│   ├── fast_dl.py                 téléchargement courant (Python + datasets)
│   ├── download_sample_books.py   téléchargeur alternatif
│   ├── verify_local_corpus.py     vérification locale (Python)
│   └── test_download_sample_books.py
├── docker-compose.yml            PostgreSQL + backend
└── front/                         application React lancée avec npm
```

Les imports `com.sorbonne.backend.gutenberg` et tous les anciens contrôleurs correspondants ont été supprimés. `LocalBookFiles` est réutilisé par la synchronisation et l'indexeur ; les noms de fichiers restent des clés de stockage. Seul le générateur de catalogue peut utiliser un nom lisible comme libellé de secours lorsque les métadonnées sont absentes.

## 3. Contrat des données locales

Le manifeste `backend/data/catalog.csv` est **généré automatiquement** par `POST /api/admin/catalog/generate` ou au début de `POST /api/admin/catalog/sync`. Il peut être fourni pour préciser les titres exacts, mais ce n'est pas obligatoire. Son en-tête reste :

```csv
storage_key,title,author,language,source_url
roman_01.txt,"Titre réel du roman, différent du fichier",Auteur Exemple,fr,
collection/x8a7.txt,"Another book",Example Writer,en,https://example.org/original
```

- `storage_key` : chemin **relatif** à `BOOK_STORAGE_ROOT`, doit se terminer par `.txt`, peut contenir des sous-dossiers. C'est une **clé technique** et non un titre.
- `title` : titre obligatoire dans le CSV généré ; si les métadonnées n'existent pas, le serveur utilise un en-tête explicite ou le nom lisible du fichier comme libellé de secours, sans modifier le `storage_key`.
- `author`, `language`, `source_url` : valeurs documentaires facultatives. `source_url` n'implique aucun téléchargement par le backend.
- Chaque entrée CSV doit occuper **une ligne** ; guillemets `"` pour encadrer les virgules, `""` pour un guillemet littéral.
- Chaque chemin doit pointer vers un fichier UTF-8 régulier sous `data/books`.
- Les fichiers sont contrôlés au moyen de leur taille, de l'encodage, d'un SHA-256 et du nombre de mots réels ; une entrée trop courte est refusée.
- Les liens symboliques échappant au répertoire, chemins absolus et `../` sont refusés.
- `fast_dl.py` utilise des noms séquentiels `book_0001.txt`, `book_0002.txt`, etc., mais vous pouvez choisir **n'importe quel autre nom** `.txt` valide : il est détecté automatiquement, sans inscription manuelle dans le CSV.
- Deux titres identiques sont possibles si les fichiers (leurs `storage_key`) sont différents. La clé unique du livre est le `storage_key`, tandis que l'API expose `bookId` (identifiant PostgreSQL).

La synchronisation ne renomme ni ne déplace les fichiers : elle ne fait que **référencer** les chemins, vérifier le contenu et mettre à jour la table `books`.

### Préparation manuelle

```bash
mkdir -p backend/data/books
cp ~/Documents/mon_roman.txt backend/data/books/roman_01.txt
```

Aucune ligne CSV à écrire : `/api/admin/catalog/sync` détecte le fichier. Pour conserver un titre réel avec une clé anonymisée, fournir un en-tête explicite ou préserver la ligne CSV d'origine. Le minimum reste **10 000 mots**.

## 4. Alimenter les fichiers avec `fast_dl.py`

Depuis la racine du dépôt, créer puis activer un environnement virtuel :

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install datasets
python scripts/fast_dl.py
deactivate
```

Sous Windows PowerShell, l'activation se fait avec `.\.venv\Scripts\Activate.ps1`. Pour réutiliser un environnement déjà créé, il suffit de le réactiver. Le fichier `.env` de Docker Compose sert à configurer les conteneurs ; il ne remplace pas `.venv`.

Le script applique les étapes suivantes :

1. Ouvre le jeu de données Hugging Face `AdhyanshVerma/pg-en`, split `train`, avec `load_dataset(..., streaming=True)`.
2. Lit le champ `text` de chaque entrée et ignore les textes vides ou de moins de 10 000 mots.
3. Écrit le texte en UTF-8 dans `backend/data/books/book_0001.txt`, puis `book_0002.txt`, etc.
4. S'arrête lorsque `target = 1664` est atteint ou lorsque le flux est épuisé ; affiche la progression tous les 100 livres et le nombre final sauvegardé.

Le chemin de sortie est relatif au répertoire courant : **lancer le script depuis la racine du dépôt**. La cible se modifie dans le script ; `fast_dl.py` ne propose pas d'argument `--target`. Le script ne filtre pas explicitement les langues et ne sauvegarde ni les métadonnées séparées ni un identifiant Gutenberg. Le backend extrait ce qu'il peut retrouver dans le texte.

**Le script ne reprend pas un téléchargement interrompu** : chaque exécution repart de `book_0001.txt` et écrase les fichiers de même nom. Il ne supprime pas les autres fichiers déjà présents dans le dossier. Si le corpus est déjà disponible, passer directement au démarrage Docker Compose et à la synchronisation. Attendre la fin du téléchargement avant de synchroniser.

`fast_dl.py` ne crée pas `catalog.csv`. Après le démarrage du backend, appeler `/api/admin/catalog/sync`, puis contrôler le corpus :

```bash
curl -X POST 'http://localhost:8080/api/admin/catalog/sync'
python3 scripts/verify_local_corpus.py --target 1664
```

Le vérificateur nécessite le CSV et utilise uniquement la bibliothèque standard Python. La procédure complète, y compris l'indexation, figure en section 14.

### Téléchargeurs alternatifs

`download_sample_books.py` et `download_books_rsync.py` restent dans le dépôt. Ils utilisent le catalogue officiel Gutenberg, écrivent leurs métadonnées dans le CSV et disposent de mécanismes de reprise différents de `fast_dl.py`. Ils ne sont pas nécessaires au lancement courant ; consulter leurs options si besoin :

```bash
python3 scripts/download_sample_books.py --help
python3 scripts/download_books_rsync.py --help
```

## 5. Migrations Flyway et base SQL

### Versions

| Version | Objet |
| --- | --- |
| V1 | `books`, `terms`, `book_terms`, ancienne table de reprise d'import |
| V2 | `local_path` devient `storage_key` ; conversion des chemins legacy |
| V3 | `book_similarities` (arêtes Jaccard) |
| V4 | `book_pageranks` et `pagerank_state` |
| **V5** | **suppression de l'identifiant Gutenberg et de la table d'import ; clé unique `storage_key`** |
| **V6** | **Restauration des valeurs par défaut de `created_at` et `updated_at`** |

**Ne pas modifier V1–V4** après qu'elles ont été appliquées : Flyway vérifie leurs checksums. V5 retire les colonnes et tables historiques, supprime les anciennes entrées non `READY` et conserve les livres `READY`, leurs IDs ainsi que leurs postings. Des IDs SQL ne démarrent pas forcément à 1 et peuvent présenter des trous.

### Tables courantes

`books`: `id`, `storage_key` **UNIQUE NOT NULL**, `title`, `author`, `language`, `word_count`, `source_url`, `content_sha256`, `content_bytes`, `status`, `created_at`, `updated_at`.

`terms`: `id`, `value` **UNIQUE**, `document_frequency`.

`book_terms`: `book_id`, `term_id`, `occurrences` (couple unique). `book_id` pointe vers `books`.

`book_similarities`: `book_a_id`, `book_b_id`, `jaccard_score`, `intersection_size`, `union_size`.

`book_pageranks`: `book_id`, `score`, `computed_at`.

`pagerank_state`: paramètres d'exécution, convergence et date de calcul.

`source_url` est une indication de provenance, jamais un endpoint réseau appelé par Spring Boot.

### Transition depuis un ancien corpus

1. Faire **une sauvegarde PostgreSQL** avant V5 (`pg_dump`) : la migration supprime les anciennes entrées non prêtes et le champ `gutenberg_id`.
2. Fournir seulement `backend/data/books/*.txt`. Le CSV est généré automatiquement s'il est absent.
3. Démarrer Spring Boot : V5 (le cas échéant) et V6 s'appliquent automatiquement.
4. Exécuter `POST /api/admin/catalog/sync?prune=true` pour générer le CSV, synchroniser les TXT et **retirer de PostgreSQL les anciens livres absents du dossier**. `prune=true` est destructif pour ces lignes ; les fichiers disque ne sont pas touchés.
5. Indexer, reconstruire Jaccard puis PageRank.

## Catalogue auto-généré et partage du projet

Le dossier `backend/data/books/` est la seule entrée obligatoire : déposer des fichiers UTF-8 `.txt`, puis appeler `POST /api/admin/catalog/sync` suffit. Le serveur examine les fichiers (y compris dans des sous-dossiers), **ne suit pas les liens symboliques**, et conserve les `storage_key` relatifs au dossier. Le manifeste `catalog.csv` est écrit atomiquement, sans réécriture inutile si son contenu n'a pas changé.

Ordre des métadonnées :

1. Valeurs déjà renseignées dans `catalog.csv` pour une même clé. Les champs vides sont complétés et un titre égal au libellé de secours du fichier peut être remplacé. Les autres valeurs restent prioritaires, même si elles diffèrent du texte.
2. Champs explicites `Title:/Titre:`, `Author:/Auteur:`, `Language:/Langue:`, `Source:/Source_URL:/URL:`. Les en-têtes UTF-8 avec BOM, CRLF et titres avec continuation indentée sont pris en charge. Les noms de langues et codes ISO sont normalisés.
3. En-tête Gutenberg ou page de titre : titre isolé ou sur plusieurs lignes, auteur précédé de `by`, `par`, `von`, `por`, `di` ou `av`. L'extraction écarte les crédits de production, notes de transcription et titres/auteurs cités après le début des chapitres. Elle reste heuristique : les mises en page inhabituelles peuvent rester sans métadonnées ou demander une correction manuelle du CSV.
4. Sans langue explicite, détection locale à partir des écritures Unicode et des mots fréquents. Le détecteur laisse le champ vide si le texte est trop court, ambigu ou non pris en charge ; il ne suppose pas que tous les livres sont en anglais.
5. Nom du fichier (exemple `Mon_roman.txt` → `Mon roman`) comme libellé de repli. Pour une clé hexadécimale opaque, l'étiquette est `Titre inconnu [12 premiers caractères]`.

L'extracteur lit au maximum 64 Kio par fichier, avec une limite de 16 384 caractères pour les métadonnées et la page de titre. `source_url` provient d'une URL HTTP(S) explicitement indiquée ou d'une référence Gutenberg dans l'en-tête (par exemple `[EBook #36]` → `https://www.gutenberg.org/ebooks/36`). Les liens vers les équipes de transcription ne sont pas des sources du livre. Aucun appel réseau n'est effectué. Un nom tel que `book_0002.txt` ne permet pas de retrouver l'identifiant Gutenberg ; les informations supprimées au téléchargement ne peuvent pas toutes être reconstituées.

Pour enrichir un ancien catalogue, redémarrer le backend puis appeler `POST /api/admin/catalog/sync` (ou `/generate` pour modifier uniquement le CSV). Une simple réindexation ne remplit pas les métadonnées. Les compteurs `inferredMetadata` et `preservedMetadata` comptent respectivement les lignes créées/enrichies et les lignes inchangées. Une seconde génération sans nouvelles informations ne réécrit pas le fichier.

Un catalogue dont les métadonnées sont précieuses peut donc être fourni avec les livres ; ce n'est pas obligatoire pour que le moteur fonctionne. Le CSV n'est pas nécessaire pour l'indexation des mots du texte ; il donne les métadonnées affichées dans les résultats.

`POST /api/admin/catalog/generate` génère le manifeste **sans toucher à la base**. `POST /api/admin/catalog/sync` le génère puis importe/mets à jour les livres valides de **10 000 mots minimum**. L'option `prune=true` ne supprime des lignes PostgreSQL que les livres absents du dossier ; elle ne supprime pas les fichiers TXT.

La migration **V6** pose les valeurs par défaut SQL de `created_at` et `updated_at`. L'`INSERT` de synchronisation renseigne également explicitement les deux horodatages pour les bases legacy dont les contraintes ont été créées différemment.

## 6. Synchronisation du catalogue local

`LocalCatalogSyncService` met à jour le CSV depuis le dossier `data/books`, refuse les clés dupliquées, vérifie chaque fichier, compte les mots, calcule SHA-256 et stocke les métadonnées en base. Une transaction englobe la synchronisation.

Comportement par clé `storage_key` :

- **absente en base** : `INSERT` de livre `READY` ; à indexer.
- **existante, contenu inchangé** : métadonnées mises à jour si nécessaire ; index préservé.
- **existante, SHA-256 modifié** : livre mis à jour et ses postings `book_terms` supprimés ; il redevient non indexé.
- **absente du dossier et `prune=true`** : suppression du livre SQL et de ses relations par FK en cascade ; aucun fichier n'est supprimé.
- **aucune modification de corpus** : ne reconstruit pas inutilement les scores.

Lors d'un changement de corpus, les fréquences documentaires sont recalculées, les termes orphelins sont supprimés, et le graphe/PageRank sont invalidés. Il faut reconstituer ces données dans cet ordre : **indexation → Jaccard → PageRank**.

Le texte est source de vérité pour `word_count` (pas la valeur du CSV ni le nombre contenu dans le nom du fichier).

## 7. Index inversé (TF)

`TermFrequencyCounter` lit chaque texte via `LocalBookFiles`, puis `TextNormalizer` : minuscule, suppression d'accents, normalisation d'apostrophes/tirets, extraction des termes en alphabet latin ASCII. Cela fournit un vocabulaire cohérent pour KMP et la recherche RegEx Java.

`terms.value` contient chaque terme unique ; `book_terms.occurrences` mémorise les occurrences de ce terme pour chaque livre. La fréquence documentaire `terms.document_frequency` est recalculée sur les postings affectés. Chaque indexation individuelle est transactionnelle et idempotente : elle supprime d'abord ses anciens postings, les reconstruit et actualise la fréquence documentaire. Les insertions des termes et postings passent par `unnest` PostgreSQL et les fréquences documentaires par **deltas d'ensembles** (termes ajoutés / retirés), évitant le recalcul de `COUNT(*)` pour chaque terme. `elapsedMillis`, `fileReadMillis`, `tokenizationMillis` et `databaseMillis` permettent de mesurer les étapes par livre.

Les termes vides sont refusés. Une réindexation explicite invalide graphe et PageRank, car les relations peuvent changer.

## 8. Recherche simple KMP

`GET /api/search?q=man&page=0&size=20` :

1. Le mot-clé (`q`) est normalisé, un seul mot est accepté.
2. `KMPSearch.prepare(q)` construit le moteur KMP **une fois**.
3. KMP inspecte la colonne des termes de l'index chargée en mémoire ; un mot-clé peut apparaître **dans** un terme (`man` dans `mankind`, `woman`, etc.).
4. Le backend récupère les postings des termes correspondants ; agrège `SUM(occurrences)` par livre.
5. Les candidats passent au ranking, puis sont paginés.

Aucun `LIKE '%mot%'` SQL n'est utilisé pour déterminer les termes qui matchent ; l'algorithme réutilise le projet 1.

## 9. Recherche avancée RegEx

`GET /api/search/advanced?regex=m(an|en)&page=0&size=20` :

```text
regex → normalisation des accents → java.util.regex.Pattern.compile(CASE_INSENSITIVE)
      → Matcher.find sur les termes → book_terms → ranking → pagination
```

Support : syntaxe Java `Pattern`, y compris `+`, `?`, `[]`, `{}`, `^`, `$`, `\d`, `\b`, etc. Motif maximal : **256 caractères**. Compilation **une seule fois** par requête puis recherche de sous-chaîne sur chaque terme avec `Matcher.find()`. Les champs d'états NFA/DFA sont supprimés de la réponse : plus aucun automate maison n'est exécuté. **Attention** : `java.util.regex` utilise aussi du backtracking ; un motif pathologique peut être très lent. Ne pas exposer ce moteur à des requêtes non fiables sans garde-fous supplémentaires.

## 10. Similarité Jaccard

Le graphe utilise les termes partagés par les livres, hors stopwords et termes trop fréquents :

\[
J(A,B) = \frac{|T_A\cap T_B|}{|T_A\cup T_B|}
\]

`T_A` et `T_B` sont les ensembles de termes discriminants des deux livres, pas les noms de fichiers ni les titres. Les couples candidats sont identifiés grâce à `book_terms`, puis les arêtes dépassant le seuil sont conservées et limitées aux meilleurs voisins (`top 20` par livre par défaut).

Paramètres de configuration : `SIMILARITY_MIN_DF=2`, `SIMILARITY_MAX_DF_RATIO=0.10`, `SIMILARITY_MIN_MAX_DF=3`, `SIMILARITY_MAX_DF_ABSOLUTE=200`, `SIMILARITY_MIN_TERM_LENGTH=3`, `SIMILARITY_MIN_SHARED_TERMS=2`, `SIMILARITY_MIN_SCORE=0.05`, `SIMILARITY_MAX_NEIGHBORS=20`.

`POST /api/admin/similarity/rebuild` remplace le graphe et **invalide PageRank**, même lorsque le nouveau graphe n'a pas d'arêtes.

## 11. PageRank pondéré et ranking final

Pour les arêtes Jaccard non orientées, la transition est symétrique et pondérée par la similarité :

\[
PR(v)=\frac{1-d}{N}+d\sum_{u\to v}\frac{PR(u)\,w(u,v)}{\sum_x w(u,x)}
\]

Le calcul inclut la répartition de la masse des nœuds sans sorties. Config : `d = 0.85`, `tolerance = 1e-10`, `maxIterations = 200`. Les scores sont conservés dans `book_pageranks` avec leur état dans `pagerank_state` ; leur somme est ≈ 1.

Pour chaque requête, le classement utilise :

\[
L_{norm}(b)=\frac{L(b)}{\max_{i\in resultats} L(i)}
\]

\[
PR_{norm}(b)=\frac{PR(b)}{\max_{i\in corpus} PR(i)}
\]

\[
finalScore(b)=0.85\,L_{norm}(b)+0.15\,PR_{norm}(b)
\]

Le score lexical brut, sa normalisation, le PageRank brut, sa normalisation et `finalScore` sont tous exposés. Si les scores PageRank sont absents ou périmés, le backend revient automatiquement au **score lexical seul** (poids 1 et 0).

## 12. Suggestions Jaccard à plusieurs graines

À partir du **top 3** des résultats classés par `finalScore`, l'agrégateur récupère les voisins Jaccard de chaque livre, exclut les graines elles-mêmes, puis calcule :

\[
recommendationScore(x)=\sum_{g\in graines}\frac{finalScore(g)}{\sum_{h\in graines}finalScore(h)}\,J(g,x)
\]

Si tous les scores de graines valent zéro, les poids sont égaux. La réponse indique `supportingSeeds`, `bestJaccardScore` et `reasons` détaillant les contributions.

Trois routes : recherche simple, recherche RegEx et voisins directs d'un seul livre. Le ranking de recherche et le ranking de recommandation restent distincts.

## 13. Référence API complète

Base : `http://localhost:8080`. Les exemples ci-dessous ne présument pas des IDs SQL ni des titres présents dans votre base ; ils dépendront du contenu réel de `catalog.csv`.

### Santé / consultation des livres

| Méthode | Route | Paramètres | Résultat |
| --- | --- | --- | --- |
| GET | `/api/health` | — | Contrôle de santé |
| GET | `/api/books` | `page=0`, `size=20` (max 100) | Pagination Spring des livres `READY` ; `id,title,author,language,wordCount,status` |
| GET | `/api/books/{id}` | `id` (ID SQL) | Métadonnées d'un livre `READY`, `404` sinon |

### Catalogue local et indexation

| Méthode | Route | Paramètres | Résultat |
| --- | --- | --- | --- |
| GET | `/api/admin/catalog/status` | — | `localFiles,catalogBooks,readyBooks,indexedBooks,minimumWordCount` |
| POST | `/api/admin/catalog/generate` | — | `localBooks,preservedMetadata,inferredMetadata,catalogUpdated` (CSV uniquement) |
| POST | `/api/admin/catalog/sync` | `prune=false` par défaut ; génère le CSV avant synchro SQL | `catalogBooks,inserted,updated,unchanged,deleted,indexAndGraphInvalidated,elapsedMillis` |
| GET | `/api/admin/indexing/status` | — | `readyBooks,indexedBooks,unindexedBooks,terms,bookTerms` |
| POST | `/api/admin/indexing/books/{bookId}` | `bookId` | `bookId,storageKey,uniqueTerms,totalTokens,elapsedMillis` |
| POST | `/api/admin/indexing/all` | `maxBooks=50` (max 2000) | `attempted,indexed,failed,books,failures,elapsedMillis` |

### Recherche KMP et RegEx

| Méthode | Route | Paramètres | Résultat |
| --- | --- | --- | --- |
| GET | `/api/search` | `q` obligatoire, `page=0`, `size=20` | Résultats KMP, scores et pagination |
| GET | `/api/search/advanced` | `regex` obligatoire, `page=0`, `size=20` | Résultats RegEx Java, scores et pagination |

Exemple de réponse simplifiée de `GET /api/search?q=man` (valeurs **illustratives**) :

```json
{
  "query":"man", "normalizedQuery":"man",
  "page":0, "size":20, "totalElements":1, "totalPages":1,
  "matchedIndexTerms":42, "pageRankApplied":true,
  "lexicalWeight":0.85, "pageRankWeight":0.15, "elapsedMillis":20,
  "content":[{
    "bookId":123, "title":"Titre tiré du CSV", "author":"Auteur réel",
    "language":"en", "wordCount":62200, "lexicalScore":100,
    "matchedTerms":13, "normalizedLexicalScore":1.0,
    "pageRankScore":0.002, "normalizedPageRankScore":0.8, "finalScore":0.97
  }]
}
```

La recherche RegEx remplace `query/normalizedQuery` par `regex/normalizedRegex` (les champs `nfaStates`, `dfaStates`, `minimizedDfaStates` ont été retirés).

### Graphe Jaccard

| Méthode | Route | Paramètres | Résultat |
| --- | --- | --- | --- |
| GET | `/api/admin/similarity/status` | — | `readyBooks,indexedBooks,storedEdges,lastBuiltAt`, configuration |
| POST | `/api/admin/similarity/rebuild` | — | nombres de candidats, arêtes, paramètres et durée |
| GET | `/api/admin/similarity/books/{bookId}/neighbors` | `limit=10` (1–100) | `bookId,neighbors[{bookId,title,author,jaccardScore,intersectionSize,unionSize}]` |

### PageRank

| Méthode | Route | Paramètres | Résultat |
| --- | --- | --- | --- |
| GET | `/api/admin/ranking/pagerank/status` | — | `available,stale,readyBooks,similarityEdges,storedScores,computedAt,similarityBuiltAt`, paramètres |
| POST | `/api/admin/ranking/pagerank/rebuild` | — | `nodeCount,edgeCount,iterations,converged,finalDelta,scoreSum,minScore,maxScore,elapsedMillis`, paramètres |
| GET | `/api/admin/ranking/pagerank/top` | `limit=10` (1–100) | `books[{bookId,title,author,pageRankScore}]` |

### Suggestions

| Méthode | Route | Paramètres | Résultat |
| --- | --- | --- | --- |
| GET | `/api/search/suggestions` | `q`, `seedCount=3`, `limit=10` | `mode=simple,input,seeds,suggestions` |
| GET | `/api/search/advanced/suggestions` | `regex`, `seedCount=3`, `limit=10` | `mode=advanced,input,seeds,suggestions` |
| GET | `/api/books/{id}/suggestions` | `limit=10` | Livre source et ses voisins Jaccard directs |

Dans les suggestions multi-graines :

- `seeds[]` : `bookId,title,author,searchRank,finalScore,seedWeight`.
- `suggestions[]` : `bookId,title,author,recommendationScore,bestJaccardScore,supportingSeeds,reasons[]`.
- `reasons[]` : `seedBookId,seedTitle,seedWeight,jaccardScore,contribution`.
- `requestedSeedCount`, `usedSeedCount`, `sourceSearchTotalElements`, `elapsedMillis` explicitent le processus.

Les réponses n'ont plus de champ `gutenbergId` ; l'ID métier de l'API est `bookId`. `storageKey` n'est donné que par l'API technique d'indexation, pas dans les résultats de recherche.

### Erreurs et validation

- CSV absent ou invalide : erreur de synchronisation (contrat et description explicite).
- Texte absent, mauvais encodage ou chemin hors racine : synchronisation refusée ; aucune moitié de corpus enregistrée (transaction).
- Livre contenant moins de 10 000 mots : refus avec compte détecté.
- Clé doublonnée dans CSV : refus.
- Recherche simple de plusieurs mots ou regex invalide : erreur HTTP 400 via les contrôleurs correspondants.
- Livre inconnu `GET /api/books/{id}` : HTTP 404.
- `maxBooks`, `limit`, `seedCount` hors limites : erreur de validation.

Les classes `*ExceptionHandler` produisent les messages JSON `{"error":"..."}` pour les erreurs explicitement traitées. Les erreurs système non couvertes utilisent le traitement standard Spring Boot.

## 14. Ordre d'exploitation et exemples shell

### Démarrage Docker Compose

Après le téléchargement des TXT décrit en section 4, lancer depuis la racine :

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

Compose construit le backend avec Maven et Java 21, puis l'exécute dans une image JRE 21. PostgreSQL 16 tourne dans le service `postgres` ; le backend attend son contrôle de santé avant de démarrer. Flyway applique les migrations au lancement de Spring Boot. Java, Maven et PostgreSQL ne sont pas requis sur l'hôte pour ce mode de lancement.

`./backend/data` est monté dans `/app/data` du backend : les TXT téléchargés sur l'hôte et le CSV généré dans le conteneur sont partagés. Ce montage doit être accessible en lecture et en écriture à l'utilisateur du conteneur. PostgreSQL conserve ses données dans le volume `postgres_data`. Seul le port **8080** du backend est publié ; PostgreSQL reste sur le réseau interne Compose.

Une fois Spring Boot démarré, quitter le suivi des logs avec `Ctrl+C` et vérifier l'API :

```bash
curl 'http://localhost:8080/api/health'
```

### Préparation de la recherche

Depuis la racine, dans cet ordre :

```bash
curl -X POST 'http://localhost:8080/api/admin/catalog/sync'
python3 scripts/verify_local_corpus.py --target 1664
curl 'http://localhost:8080/api/admin/catalog/status'
curl -X POST 'http://localhost:8080/api/admin/indexing/all?maxBooks=2000'
curl 'http://localhost:8080/api/admin/indexing/status'

# Attendre unindexedBooks=0 avant de construire le graphe puis PageRank.
curl -X POST 'http://localhost:8080/api/admin/similarity/rebuild'
curl -X POST 'http://localhost:8080/api/admin/ranking/pagerank/rebuild'
curl 'http://localhost:8080/api/admin/ranking/pagerank/status'
curl 'http://localhost:8080/api/search?q=man&page=0&size=20'
curl --get 'http://localhost:8080/api/search/advanced' --data-urlencode 'regex=m(an|en)'
curl --get 'http://localhost:8080/api/search/suggestions' --data-urlencode 'q=man'
```

Compose ne déclenche pas ces traitements automatiquement. Si `sync` a invalidé l'index pour certains livres, `/api/admin/indexing/all` ne traite que les livres sans postings. En cas d'interruption, relancer jusqu'à `unindexedBooks=0` et vérifier les échecs dans les réponses. Pour retirer de la base les livres absents du dossier, utiliser `sync?prune=true` ; le bouton de synchronisation du frontend emploie cette option.

### Frontend local

Dans un autre terminal, depuis la racine, avec Node.js 22.22.0 minimum et npm :

```bash
cd front
npm install
npm run build
npm run dev
```

Le frontend se lance séparément de Docker Compose. Ouvrir l'adresse indiquée par Vite, généralement `http://localhost:5173`. Il appelle l'API `http://localhost:8080` par défaut. `npm run start` permet de servir le build produit à la place du serveur de développement. Voir le [README frontend](../front/README.md) pour `VITE_API_BASE_URL` et les vérifications TypeScript.

### Arrêt et mises à jour

Depuis la racine :

```bash
# Redémarrage habituel.
docker compose up -d

# Après une modification du backend.
docker compose up -d --build backend

# Arrêt sans suppression des données PostgreSQL.
docker compose down
```

Arrêter le frontend avec `Ctrl+C`. L'option `down -v` efface le volume PostgreSQL. Après modification du corpus, reprendre synchronisation → indexation → Jaccard → PageRank.

### Vérifications SQL dans le conteneur

Le port PostgreSQL n'étant pas publié sur l'hôte, ouvrir une session SQL via Compose :

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Puis exécuter :

```sql
SELECT COUNT(*) AS livres FROM books WHERE status='READY';
SELECT COUNT(*) AS termes FROM terms;
SELECT COUNT(*) AS postings FROM book_terms;
SELECT COUNT(*) AS aretes_jaccard FROM book_similarities;
SELECT COUNT(*) AS scores_pagerank FROM book_pageranks;
SELECT id, title, storage_key, word_count FROM books ORDER BY id LIMIT 10;
```

Un corpus synchronisé de 1 664 fichiers valides donne 1 664 livres `READY` si le CSV contient exactement ces entrées et que la base ne conserve aucune ancienne entrée supplémentaire. Le nombre de postings, d'arêtes et de scores dépend du contenu et des seuils réels.

## 15. Configuration

### Configuration Docker Compose

Compose lit les variables du shell ou d'un fichier `.env` placé à la racine. Les valeurs par défaut de développement sont :

| Variable Compose | Valeur par défaut | Fonction |
| --- | --- | --- |
| `DB_NAME` | `idrisdatabase` | Nom de la base créée par PostgreSQL et utilisée par le backend |
| `DB_USER` | `ai222829` | Utilisateur PostgreSQL |
| `DB_PASSWORD` | `devpassword` | Mot de passe de développement |

Le service backend reçoit `DB_URL=jdbc:postgresql://postgres:5432/<DB_NAME>`, `BOOK_STORAGE_ROOT=/app/data/books`, `BOOK_CATALOG=/app/data/catalog.csv` et `BOOK_MIN_WORDS=10000`. Le nom `postgres` est résolu sur le réseau Compose. Le fichier `.env` n'est pas un environnement virtuel Python et ne doit pas être commité.

Les paramètres `POSTGRES_*` initialisent un volume vide : modifier les valeurs de `.env` ne renomme pas automatiquement une base existante et ne change pas son mot de passe. Les autres variables applicatives ne sont transmises au conteneur que si elles sont déclarées dans son bloc `environment`.

### Variables de l'application Spring Boot

Les valeurs suivantes viennent d'`application.yaml`. Compose remplace les chemins et paramètres de connexion comme indiqué ci-dessus :

| Variable | Valeur par défaut | Fonction |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/idrisdatabase` | base PostgreSQL |
| `DB_USER` | `ai222829` | utilisateur PostgreSQL de développement |
| `DB_PASSWORD` | requis | secret local |
| `BOOK_STORAGE_ROOT` | `./data/books` | racine des textes (chemin relatif au dossier de lancement backend) |
| `BOOK_CATALOG` | `./data/catalog.csv` | manifeste CSV |
| `BOOK_MIN_WORDS` | `10000` | seuil, ne peut pas être abaissé sous 10 000 |
| `BOOK_MAX_TEXT_BYTES` | `25000000` | borne par fichier |
| `LEXICAL_WEIGHT` | `0.85` | pondération lexicale |
| `PAGERANK_WEIGHT` | `0.15` | pondération PageRank dans le classement |
| `RANKING_PAGERANK_WEIGHT` | `0.15` | pondération PageRank |
| `PAGERANK_DAMPING_FACTOR` | `0.85` | amortissement |
| `PAGERANK_TOLERANCE` | `1e-10` | convergence |
| `PAGERANK_MAX_ITERATIONS` | `200` | itérations max |
| `SUGGESTION_SEED_COUNT` | `3` | nombre de graines |
| `SUGGESTION_LIMIT` | `10` | suggestions max par défaut |

Autres paramètres `SIMILARITY_*` et `SUGGESTION_*` sont définis et commentés dans `application.yaml`. **Aucune clé Gutenberg/RapidAPI ni configuration d'import serveur.**

## 16. Tests, limites et sécurité

Depuis la racine, après génération du catalogue :

```bash
python3 scripts/verify_local_corpus.py --target 1664
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Les tests Python concernent les téléchargeurs alternatifs ; ils ne constituent pas un test réseau de `fast_dl.py`. Vérifier le corpus effectivement téléchargé et les compteurs `catalogBooks`, `readyBooks` et `indexedBooks`.

Pour les tests Java, installer un JDK 21 ou supérieur et Maven sur l'hôte. Les tests unitaires peuvent être lancés sans le test de démarrage Spring :

```bash
mvn -f backend/pom.xml -Dtest='*,!BackendApplicationTests' test
```

La suite complète (`mvn -f backend/pom.xml test`) inclut `BackendApplicationTests`, qui nécessite une base PostgreSQL accessible avec `DB_URL`, `DB_USER` et `DB_PASSWORD` configurés. Le Compose courant ne publie pas PostgreSQL sur `localhost:5432`. La construction Docker utilise `-DskipTests` et ne remplace donc pas l'exécution des tests.

Pour vérifier le frontend :

```bash
cd front
npm run typecheck
npm run build
```

- Les fichiers sont UTF-8 après téléchargement ; le backend est exclusivement local.
- Ne committer **ni les livres ni les identifiants de base**. Le `data` réel est exclu de Git (sauf exemple CSV et `.gitkeep`).
- Sécuriser `/api/admin/**` avant de l'exposer hors localhost ; ce projet est initialement conçu pour une démonstration académique sur poste de développement.
- La recherche simple KMP et la recherche avancée RegEx Java parcourent le dictionnaire `terms`. Le temps et la mémoire dépendent surtout du nombre de termes, pas du nom des fichiers.
- Le graphe Jaccard repose sur l'index inversé et des seuils de fréquence ; un changement de contenu nécessite un rebuild du graphe et du PageRank.
- Pour démontrer l'application sur plusieurs machines, héberger le backend sur un serveur partagé avec PostgreSQL et rendre `BOOK_STORAGE_ROOT` accessible à ce serveur. Un navigateur client n'a **pas besoin** de disposer des livres : il interroge uniquement les routes HTTP.
