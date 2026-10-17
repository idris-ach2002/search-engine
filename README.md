# DAAR Search Engine

Moteur de recherche académique sur un corpus de **1 664+ livres Project Gutenberg**, avec index inversé, recherche KMP/RegEx, graphe de similarité Jaccard, PageRank pondéré et recommandations.

## Fonctionnalité du backend

-  acquisition Gutenberg et stockage local portable ;
-  indexation `terms` / `book_terms` ;
-  recherche simple avec KMP ;
-  recherche avancée RegEx avec `SyntaxTree → NFA → DFA → Hopcroft` ;
-  graphe de similarité Jaccard ;
-  PageRank pondéré ;
-  ranking final lexical + PageRank ;
-  suggestions depuis les meilleurs résultats et depuis un livre.

## Documentation

La documentation backend complète est ici :

**[docs/BACKEND.md](docs/BACKEND.md)**

Elle contient :

- l'architecture et la pipeline complète ;
- le modèle PostgreSQL et les migrations Flyway ;
- les algorithmes et leurs formules ;
- les **21 routes HTTP**, paramètres, réponses et erreurs ;
- la configuration par variables d'environnement ;
- les procédures d'import, indexation, rebuild Jaccard et PageRank ;
- le ranking final et les recommandations ;
- les résultats de référence du corpus de développement ;
- un scénario de démonstration.

## Démarrage rapide

```bash
cd backend
export DB_PASSWORD='votre_mot_de_passe_local'
mvn clean test
mvn spring-boot:run
```

Puis :

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/admin/indexing/status
curl "http://localhost:8080/api/search?q=man"
```

## Pipeline

```text
Gutenberg
  ↓
stockage + books
  ↓
indexation terms / book_terms
  ↓
KMP ou RegEx
  ↓
pertinence lexicale
  ↓
Jaccard → PageRank
  ↓
ranking final
  ↓
suggestions
```

## Backend

- Java / Spring Boot
- PostgreSQL
- Flyway
- Maven

Le frontend React est situé dans `front/`.
