# DAAR — Frontend

Interface React Router du moteur de recherche documentaire : recherche KMP et
RegEx, recommandations du graphe Jaccard, fiche détaillée des livres et
monitoring des traitements.

## Démarrer

Depuis ce dossier :

```bash
npm ci
npm run dev
```

Le frontend utilise `http://localhost:8080` comme adresse API par défaut. Pour
un autre environnement, définir `VITE_API_BASE_URL` avant de lancer Vite, par
exemple :

```bash
VITE_API_BASE_URL=http://localhost:8080 npm run dev
```

Le backend doit autoriser l’origine du frontend via CORS.

## Vues et endpoints

- **Recherche** — `GET /api/search` et `GET /api/search/advanced`, pagination
  par 10 résultats, observabilité KMP/automates, score lexical/PageRank et
  suggestions via `/api/search/suggestions` ou
  `/api/search/advanced/suggestions`.
- **Fiche livre** — `GET /api/books/{id}` et
  `GET /api/books/{id}/suggestions`.
- **Monitoring** — états Gutenberg, index, graphe Jaccard et PageRank, actions
  de reconstruction, classement global PageRank.

## Organisation du code

- `app/routes/` — composition de la navigation et des vues de route.
- `app/components/` — composants React d’interface, un composant exporté par fichier.
- `app/hooks/` — état et effets liés à la recherche, aux livres et au monitoring.
- `app/types/` — contrats TypeScript des réponses API et de l’interface.
- `app/api.ts` — client HTTP typé pour les endpoints du backend.
- `app/lib/` — fonctions partagées de formatage et de validation.
- `app/app.css` — styles responsives de l’application.

Les composants, hooks et modèles exposent leurs responsabilités dans des
commentaires en anglais. Le client API centralise les erreurs réseau et HTTP ;
les vues affichent séparément les échecs de chaque ressource.

Vérifications locales :

```bash
npm run typecheck
npm run build
```
