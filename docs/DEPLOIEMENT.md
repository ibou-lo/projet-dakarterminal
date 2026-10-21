# Déploiement — Vercel + Render, application mobile

Dépôt GitHub : [ibou-lo/projet-dakarterminal](https://github.com/ibou-lo/projet-dakarterminal).

Poste web : [https://dakar-autotrack-web.vercel.app](https://dakar-autotrack-web.vercel.app) (projet Vercel `dakar-autotrack-web`, racine `frontend`).

API : [https://autotrack-api-if5d.onrender.com](https://autotrack-api-if5d.onrender.com) (service Render `autotrack-api`, Docker JDK 17 + PostgreSQL). Tableau de bord : [srv-dbca9kfavr4c73fc3v60](https://dashboard.render.com/web/srv-dbca9kfavr4c73fc3v60).

Démo en ligne : comptes README conservés (`AUTOTRACK_PRODUCTION` reste `false`). HTTPS est fourni par Render et Vercel.

```
Poste web (Vercel)  --/api-->  API Spring Boot (Render)  -->  PostgreSQL (Render)
Flutter (Chrome / Android)  --HTTPS-->  même API
```

Les photos restent sur le disque du conteneur Render : elles disparaissent à chaque redéploiement (instance Free).

Le plan **Free** s’endort après inactivité : le premier appel peut prendre ~50 s.

## 1. Render (API + PostgreSQL)

Blueprint : [New → Blueprint](https://dashboard.render.com/blueprints/new) → `ibou-lo/projet-dakarterminal`.

Ou en CLI, depuis la racine du dépôt (après `render login`) :

```bash
render blueprints apply render.yaml
```

| Variable | Origine |
|---|---|
| `DATABASE_URL` | injectée depuis `autotrack-db` |
| `AUTOTRACK_JWT_SECRET` / `AUTOTRACK_TICKET_SECRET` | générés par Render |
| `AUTOTRACK_ALLOWED_ORIGINS` | `https://*.vercel.app`, localhost, `https://*.onrender.com` |
| `DEMO_SEED` | `true` |
| `DDL_AUTO` | `update` |
| `API_DOCS` | `false` |
| `JAVA_TOOL_OPTIONS` | heap borné (instance 512 Mo) |

`PORT` est fourni par Render. Root Directory Docker : `backend`.

Après le premier déploiement :

```bash
curl -s https://<service>.onrender.com/api/health
curl -s -X POST https://<service>.onrender.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"superviseur","password":"Superviseur@2026"}'
```

Un jeton `accessToken` doit être renvoyé. Le premier `curl` peut durer une minute si l’instance dort.

## 2. Vercel (poste web)

Root Directory : `frontend`. Build : `node scripts/inject-api-base.js && npm run build`. Output : `dist/dakar-autotrack-web/browser`.

| Variable | Valeur |
|---|---|
| `API_BASE_URL` | `https://autotrack-api-if5d.onrender.com` |

(`RAILWAY_API_URL` est encore lu en repli.)

Connexion : `superviseur` / `Superviseur@2026`. Pages : `/parc`, un passeport VIN, `/enlevements`.

## 3. Application mobile

APK release (API Render déjà compilée) :

- `docs/dakar-autotrack.apk`
- `mobile/build/app/outputs/flutter-apk/app-release.apk`

Transférer le fichier sur le téléphone, autoriser l’installation depuis une source inconnue, ouvrir l’APK.

Rebuild :

```bash
cd mobile
flutter build apk --release --dart-define=API_BASE_URL=https://autotrack-api-if5d.onrender.com
```

```bash
cd mobile
flutter run -d chrome --dart-define=API_BASE_URL=https://autotrack-api-if5d.onrender.com
flutter run --dart-define=API_BASE_URL=https://autotrack-api-if5d.onrender.com
```

Comptes : `agent.diallo` / `Agent@2026` ; client BL `DKR-88341`, code SMS `5821`.

Sur Chrome, la lecture automatique du VIN (ML Kit) n’est pas disponible : saisie manuelle.
