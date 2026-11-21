# DAAR — Frontend

Interface React Router du moteur de recherche documentaire : recherche KMP et RegEx Java, recommandations du graphe Jaccard, fiche détaillée des livres et monitoring des traitements.

## Démarrer

Utiliser Node.js **22.22.0 minimum** et npm. PostgreSQL et le backend doivent être démarrés avec Docker Compose depuis la racine du dépôt :

```bash
docker compose up -d --build
```

Pour préparer les livres avec `scripts/fast_dl.py` dans un environnement virtuel Python, puis synchroniser et indexer le corpus, suivre le [README principal](../README.md). Le frontend se lance séparément des conteneurs.

Dans un autre terminal, depuis la racine :

```bash
cd front
npm install
npm run build
npm run dev
```

Ouvrir l'adresse affichée par Vite, généralement `http://localhost:5173`. Quitter le serveur de développement avec `Ctrl+C`.

| Commande, depuis `front/` | Rôle |
| --- | --- |
| `npm install` | Installer les dépendances |
| `npm run build` | Produire le build React Router dans `build/` |
| `npm run dev` | Lancer le serveur de développement |
| `npm run start` | Servir le build déjà produit, à la place du serveur de développement |
| `npm run typecheck` | Générer les types de routes et vérifier TypeScript |

`npm run dev` peut être relancé seul après l'installation ; il n'utilise pas le build de production. `npm ci` est aussi disponible pour une installation reproductible depuis `package-lock.json`.

## Adresse du backend

Le frontend utilise `http://localhost:8080` comme adresse API par défaut. Pour une autre adresse, définir `VITE_API_BASE_URL` :

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

Pour un build destiné à une autre API, définir la variable **pendant le build** :

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run build
npm run start
```

Le navigateur doit pouvoir joindre cette adresse. La configuration CORS actuelle du backend autorise les origines HTTP `localhost` et `127.0.0.1`, quel que soit le port ; une autre origine nécessite une adaptation côté backend.

## Vues et endpoints

- **Recherche** — `GET /api/search` et `GET /api/search/advanced`, pagination par 10 résultats, métriques KMP/RegEx, score lexical/PageRank et suggestions via `/api/search/suggestions` ou `/api/search/advanced/suggestions`.
- **Fiche livre** — `GET /api/books/{id}` et `GET /api/books/{id}/suggestions`.
- **Monitoring** — état du catalogue local, index, graphe Jaccard et PageRank, actions de synchronisation et de reconstruction, classement global PageRank.

Le bouton **Synchroniser le catalogue** appelle `/api/admin/catalog/sync?prune=true` : il importe les TXT et retire de la base les livres absents du dossier local. Il ne télécharge pas les livres. Après une modification du corpus, effectuer dans l'ordre : synchronisation → indexation → Jaccard → PageRank.

## Organisation du code

- `app/routes/` — composition de la navigation et des vues de route.
- `app/components/` — composants React d'interface, un composant exporté par fichier.
- `app/hooks/` — état et effets liés à la recherche, aux livres et au monitoring.
- `app/types/` — contrats TypeScript des réponses API et de l'interface.
- `app/api.ts` — client HTTP typé pour les endpoints du backend.
- `app/lib/` — fonctions partagées de formatage et de validation.
- `app/app.css` — styles responsives de l'application.

Le client API centralise les erreurs réseau et HTTP ; les vues affichent séparément les échecs de chaque ressource.

## Vérifications locales

Depuis `front/` :

```bash
npm run typecheck
npm run build
```
