# Déploiement — Vercel, Railway, application mobile

Dépôt GitHub : [ibou-lo/projet-dakarterminal](https://github.com/ibou-lo/projet-dakarterminal).

Démo en ligne : comptes et données de démonstration conservés (`AUTOTRACK_PRODUCTION` reste `false`). HTTPS est fourni par Railway et Vercel.

```
Poste web (Vercel)  --/api-->  API Spring Boot (Railway)  -->  PostgreSQL (Railway)
Flutter (Chrome / Android)  --HTTPS-->  même API
```

Les photos restent sur le disque du conteneur Railway : elles disparaissent à chaque redéploiement.

## 1. Variables Railway (API)

Lier un service **PostgreSQL** au service backend. `DATABASE_URL` est alors injecté et converti en JDBC au démarrage.

| Variable | Valeur |
|---|---|
| `AUTOTRACK_JWT_SECRET` | secret ≥ 32 caractères, **différent** de la valeur de démo du dépôt |
| `AUTOTRACK_TICKET_SECRET` | secret ≥ 32 caractères, **différent** de la valeur de démo du dépôt |
| `AUTOTRACK_ALLOWED_ORIGINS` | `https://*.vercel.app,http://localhost:*,http://127.0.0.1:*` |
| `DEMO_SEED` | `true` |
| `DDL_AUTO` | `update` |
| `API_DOCS` | `false` |
| `AUTOTRACK_PRODUCTION` | `false` (comptes README encore utilisés) |

`PORT` est fourni par Railway. Inutile de poser `DB_URL` / `DB_USER` / `DB_PASSWORD` si Postgres Railway est lié.

Root Directory du service : `backend`. Builder : Dockerfile ([`backend/Dockerfile`](../backend/Dockerfile), JDK 17).

Créer le projet : [Railway → New → GitHub](https://railway.app/new) → `ibou-lo/projet-dakarterminal` → Root Directory `backend` → Add Plugin **PostgreSQL** → coller les variables ci-dessus. Les CLI `railway` / `vercel` doivent être connectées (`railway login`, `vercel login`) pour un déploiement en ligne de commande.

Après le premier déploiement, tester :

```bash
curl -s -X POST https://<app>.up.railway.app/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"superviseur","password":"Superviseur@2026"}'
```

Un jeton `accessToken` doit être renvoyé.

## 2. Variables Vercel (poste web)

Root Directory : `frontend`. Build : `node scripts/inject-api-base.js && npm run build`. Output : `dist/dakar-autotrack-web/browser`.

Importer : [Vercel → Add New → Project](https://vercel.com/new) → `ibou-lo/projet-dakarterminal` → Root Directory `frontend` → variable `RAILWAY_API_URL`.

| Variable | Valeur |
|---|---|
| `RAILWAY_API_URL` | URL publique Railway, **sans** slash final, ex. `https://autotrack-xxxx.up.railway.app` |

[`frontend/vercel.json`](../frontend/vercel.json) réécrit `/api/:path*` vers cette URL et renvoyer les routes Angular vers `index.html`. Le script d’injection recopie aussi l’URL dans le bundle (filet si la réécriture n’est pas interpolée).

Déployer **Railway d’abord**, renseigner `RAILWAY_API_URL`, puis déployer Vercel.

Connexion : `superviseur` / `Superviseur@2026`. Pages à ouvrir : `/parc`, un passeport VIN, `/enlevements`.

## 3. Application mobile

L’app Flutter n’utilise pas le rewrite Vercel : elle appelle Railway en direct.

```bash
cd mobile
# Chrome
flutter run -d chrome --dart-define=API_BASE_URL=https://<app>.up.railway.app
# Émulateur ou téléphone Android
flutter run --dart-define=API_BASE_URL=https://<app>.up.railway.app
```

Comptes : `agent.diallo` / `Agent@2026` ; client BL `DKR-88341`, code SMS `5821`.

Sur Chrome, la lecture automatique du VIN (ML Kit) n’est pas disponible : saisie manuelle. Les photos s’affichent en mémoire.

Si `flutter emulators` ne liste rien : créer un appareil dans Android Studio → Device Manager.
