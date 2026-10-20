# Dakar AutoTrack — prototype

**Le passeport numérique des véhicules au terminal.** Retrouver chaque véhicule, connaître son état et vérifier s'il peut sortir, depuis une seule interface.

> Prototype de démonstration : **toutes les données sont fictives**. Les intégrations (systèmes du terminal, plateforme de formalités, passerelle SMS, portiques RFID) sont simulées.

| Dossier | Technologie | Rôle |
|---|---|---|
| `backend/` | Java 17 · Spring Boot 3.3 · Spring Security + **JWT** · JPA (H2 / PostgreSQL) | API REST, règles métier, assistant IA, sécurité |
| `frontend/` | **Angular 18** (composants standalone, signaux) | Poste de supervision : parc, véhicules, mouvements, enlèvements, indicateurs, fraîcheur, portique, chat IA |
| `mobile/` | **Flutter 3** (Android / iOS) | Agents de parc (réception, place, déplacement, rafale, assistant), agent de portail, espace client |
| `tools/` | Python + reportlab | Génération des étiquettes QR de la maquette physique |
| `docs/` | | Architecture, API, étiquettes de démonstration (PDF) |

---

## 1. Démarrage rapide

### Prérequis
- JDK 17 ou plus, Maven 3.9+
- Node.js 20+ et npm (Angular CLI installé par `npm install`)
- Flutter 3.24+ (pour l'application mobile), Android Studio ou Xcode

### Backend (port 8080)
```bash
docker compose up -d postgres     # base PostgreSQL (voir § 7 pour une installation sans Docker)
cd backend
mvn spring-boot:run               # ou demarrer.cmd sous Windows
```
- Swagger UI : http://localhost:8080/swagger-ui.html (bouton **Authorize** → coller le jeton)
- Console H2 (profil `h2` uniquement) : http://localhost:8080/h2-console (JDBC `jdbc:h2:mem:autotrack`, utilisateur `sa`)
- Tests (rejouent le scénario de démo) : `mvn test`

### Frontend web (port 4200)
```bash
cd frontend
npm install
npm start          # ng serve avec proxy /api → http://localhost:8080
```
Ouvrir http://localhost:4200

### Application mobile
```bash
cd mobile
flutter create . --platforms=android,ios --project-name dakar_autotrack   # génère android/ et ios/ (une seule fois)
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080                # émulateur Android
# téléphone réel sur le même Wi-Fi : --dart-define=API_BASE_URL=http://<IP_du_PC>:8080
```
Permissions à ajouter après `flutter create` : voir [`mobile/README.md`](mobile/README.md).

---

## 2. Comptes de démonstration

| Rôle | Identifiant | Mot de passe | Accès |
|---|---|---|---|
| Administrateur | `admin` | `Admin@2026` | comptes, profils & droits, journal d'audit (pas d'opérations du parc) |
| Superviseur de parc | `superviseur` | `Superviseur@2026` | tout, dont indicateurs, simulation du BAE |
| Agent de parc (zone B) | `agent.diallo` / `agent.ba` | `Agent@2026` | réception, place, déplacements, rafale, assistant |
| Agent de parc (zone A) | `agent.faye` | `Agent@2026` | idem |
| Agent de portail | `portail.sarr` | `Portail@2026` | portique / contrôle de sortie |
| Client (mobile) | BL `DKR-88341` | code SMS `5821` | son véhicule uniquement |

---

## 3. Scénario de démonstration (3 minutes)

1. **Mobile, agent** : *Réceptionner un véhicule* → photo du VIN → lecture assistée (OCR sur le téléphone + contrôle serveur, caractère incertain signalé) → **Confirmer le VIN**.
2. **État** : 8 photos, réserve « rayure portière AR gauche », carburant, clés → **Enregistrer l'état**.
3. **Place** : place suggérée → **scan du QR de la place** (`PL:B-3-18`, voir `docs/etiquettes-demo.pdf`) → passeport créé.
4. **Web, superviseur** (`/parc`) : le véhicule `VF3LCYHZPJS218456` est en **B · 3 · 18**, « **Véhicule localisé — autorisation de sortie en attente** ».
5. **Simuler la réception du BAE** → statut **« Prêt à enlever »** → **Générer le ticket de retrait** (QR signé HMAC, 48 h, usage unique).
6. **Mobile, client** (BL + code) : le ticket s'affiche avec son QR.
7. **Mobile, portail** (`portail.sarr`) : scan du ticket → contrôles automatiques (ticket, BAE, paiement) + contrôles manuels (identité, état) → **Valider la sortie**. Un second scan du même ticket est **bloqué**.

Bonus IA : dans `/assistant`, « *Où est le véhicule …218456 et que manque-t-il ?* », puis « *Préviens le transitaire dès que le BAE arrive, et prépare le ticket* » → action proposée, **validée par un humain**, journalisée.

---

## 4. Fonctionnalités couvertes (maquette → code)

| Écran de la maquette | Web (Angular) | Mobile (Flutter) | API |
|---|---|---|---|
| Supervision du parc + passeport numérique | `/parc` | — | `GET /api/park`, `GET /api/vehicles/{vin}` |
| Gestion des zones du plan (ajout, allées, renommage, suppression) | `/parc` → **+ Ajouter une zone** (superviseur) | liste des zones dans `RafaleScreen` | `GET/POST /api/zones`, `PUT/DELETE /api/zones/{code}`, `POST /api/zones/{code}/allees` |
| 1 · Lecture assistée du VIN | — | `ReceptionVinScreen` (ML Kit) | `POST /api/vehicles/vin-check`, `POST /api/vehicles` |
| 2 · État à la réception | historique du passeport | `EtatScreen` (photos, réserves) | `POST /api/vehicles/{vin}/photos`, `PUT …/inspection` |
| 3 · Affectation de place | plan du parc | `PlaceScreen` + scanner QR | `GET /api/park/suggest-place`, `POST …/park` |
| Simulation BAE → Prêt à enlever | bouton du passeport | — | `POST …/formalities/BAE/receive` |
| 4 · Ticket de retrait client | carte ticket | `ClientScreen` (QR) | `POST …/ticket`, `GET /api/client/me` |
| 5 · Contrôle de sortie | `/portique` | `PortailScreen` | `POST /api/gate/scan`, `POST /api/gate/passages/{id}/decision` |
| 6 · Chat IA agent (voix + photo) | — | `ChatScreen` (speech_to_text, OCR) | `POST /api/assistant/chat` |
| 7 · Chat IA client | — | `ClientScreen` (suivi + alerte SMS) | `POST /api/client/notify` |
| Chat IA superviseur + actions validées | `/assistant` | — | `POST /api/assistant/actions/{id}` |
| Module Véhicules | `/vehicules` | — | `GET /api/vehicles?status&zone&q` |
| Module Mouvements | `/mouvements` | déplacement | `GET /api/movements…`, `POST /api/tasks` |
| Module Enlèvements (tableau) | `/enlevements` | — | `GET /api/pickups/board` |
| Module Indicateurs pilote | `/indicateurs` | — | `GET /api/indicators` |
| Portique RFID au portail | `/portique` (lecture EPC simulée) | — | `POST /api/gate/scan` (`rfidEpc`) |
| Carte « fraîcheur » + incohérences | `/fraicheur` | — | `GET /api/park?source=`, `GET /api/park/incoherences` |
| 8 · Inventaire en rafale | — | `RafaleScreen` (QR multiples) | `POST /api/park/sweep` |
| Portique RFID d'entrée de zone | `/portique` (portiques de zone simulés) | — | `POST /api/park/zone-read` |

### Innovations QR & RFID

| Innovation | Web (Angular) | Mobile (Flutter) | API |
|---|---|---|---|
| Étiquette hybride QR + RFID (dont étiquette suspendue réutilisable, libérée au portail) | `/etiquettes` | `TagKeyScreen` (scan `VH:` + lecture EPC) | `GET /api/tags`, `POST /api/vehicles/{vin}/tag` |
| Étiquettes imprimables (véhicules et places `PL:`) | `/etiquettes` → Imprimer | — | `GET /api/labels/places?zone&allee` |
| Armoire à clés (QR `KEY:<VIN>`) + contrôle « Clés » au portail | `/cles` | `TagKeyScreen` (Sortir / Déposer / Remise) | `GET /api/keys?state`, `POST /api/keys/{vin}` |
| Chaîne de garde (lectures QR, portiques, rafale) | historique du passeport | — | `GET /api/vehicles/{vin}` |
| QR d'accueil client : file d'attente et horaires du portail | — | `ClientScreen` | `GET /api/client/me` (`queueAhead`, `gateHours`) |
| Inventaire en rafale + carte « fraîcheur » | `/fraicheur` | `RafaleScreen` | `POST /api/park/sweep` |

---

## 5. Sécurité (JWT)

> Revue détaillée (OWASP ASVS, API Top 10, loi n° 2008-12) et checklist de mise en production : [`docs/SECURITE.md`](docs/SECURITE.md).

- Authentification **stateless** : `POST /api/auth/login` renvoie un **jeton d'accès** (HS256, 60 min) et un **jeton de rafraîchissement** (12 h) ; `POST /api/auth/refresh` ; le jeton de rafraîchissement est refusé pour appeler l'API.
- Claims : `sub`, `role`, `name`, `zone`, `bl` (clients), `typ`, `iss`, `jti`, `exp`.
- Rôles : `SUPERVISEUR`, `AGENT`, `PORTAIL`, `CLIENT` — règles par URL (`SecurityConfig`) + `@PreAuthorize` sur les actions sensibles.
- Client : connexion **BL + code SMS** ; le BL est porté par le JWT, jamais par un paramètre → un client ne voit que son véhicule.
- Mots de passe **BCrypt (coût 12)** ; blocage après 5 échecs ; réponses 401/403 en JSON ; CORS limité aux origines locales et au réseau interne (`autotrack.security.allowed-origins`).
- Tickets de retrait : QR **signé HMAC-SHA256**, expiration, **usage unique** vérifié au portail.
- Web : jetons en `sessionStorage` + intercepteur avec rafraîchissement automatique. Mobile : **flutter_secure_storage** (Keychain / Keystore).
- **À faire avant tout pilote** : secrets via variables d'environnement (`AUTOTRACK_JWT_SECRET`, `AUTOTRACK_TICKET_SECRET`), HTTPS, rotation/révocation des jetons, journal d'audit persistant, désactivation de la console H2 (profil `postgres`).

### Accès aux menus par profil (web)

Tableau des **valeurs par défaut** ; l'administrateur peut les modifier dans *Administration → Profils & privilèges* (menu, routes et API suivent les privilèges effectifs renvoyés par le serveur).

| Menu | Superviseur | Agent de parc | Agent de portail |
|---|:-:|:-:|:-:|
| Parc (plan + passeport) | ✔ | ✔ | consultation |
| Véhicules · Tickets · Clés · Assistant IA | ✔ | ✔ | ✔ |
| Affectation · Mouvements · Enlèvements · Étiquettes · Fraîcheur | ✔ | ✔ | — |
| Portique | ✔ | — | ✔ (page d'accueil) |
| Indicateurs pilote · gestion des zones · simulation BAE | ✔ | — | — |

Une page non autorisée (URL tapée à la main) renvoie vers l'accueil du profil avec un message « Accès refusé ». Mobile : agent / superviseur → espace agent ; portail → contrôle de sortie ; client → suivi de son véhicule.

### Administration (comptes et audit)

Profil **Administrateur** (`admin`), menu **Administration** du poste web :
- **Utilisateurs** : liste filtrable, création (mot de passe provisoire affiché une seule fois), modification du profil et de la zone, désactivation / réactivation (sessions coupées immédiatement), déverrouillage, réinitialisation du mot de passe, lien vers l'activité du compte.
- **Profils & privilèges** : l'administrateur coche, pour chaque profil, les **pages** (menu du poste web) et les **actions** (réception, stationnement, ticket, formalités, contrôle de sortie, zones, clés, étiquettes, comptes…) autorisées. Enregistrement groupé, journalisé (« Privilèges modifiés »), effet immédiat côté serveur et menus mis à jour sans reconnexion ; bouton « Rétablir les valeurs par défaut ». Les privilèges d'administration de l'administrateur sont verrouillés. Catalogue : `backend/.../security/Privilege.java`.
- **Journal d'audit** (aussi en lecture pour le superviseur) : connexions, échecs, verrouillages, refus d'accès, gestion des comptes et actions métier (réception, ticket, décision au portail, zones…), filtres et export CSV.
- **Mon compte** (tous les profils) : changement de son mot de passe (10 caractères minimum, lettres et chiffres).

Règles : verrouillage 15 min après 5 échecs ; un administrateur ne peut ni se désactiver ni changer son propre profil ; il reste toujours un administrateur actif. API : `/api/admin/users…`, `/api/admin/audit`, `/api/admin/overview`, `/api/auth/password`.

## 6. Intelligence artificielle

- **Lecture assistée du VIN** : OCR sur le téléphone (Google ML Kit, hors ligne) ; le serveur normalise (I→1, O/Q→0), vérifie le format ISO 3779, la clé de contrôle et signale les caractères ambigus. **L'agent confirme toujours.**
- **Assistant** (`RuleBasedAssistantEngine`) : répond uniquement à partir des données, cite ses sources, **propose** des actions qu'un humain valide. L'interface `AssistantEngine` permet de brancher un LLM plus tard sans changer les garde-fous.
- La comparaison automatique de photos reste une **évolution**, présentée comme aide à la vérification.

## 7. Base de données PostgreSQL (par défaut)

Le backend démarre sur **PostgreSQL** (profil `postgres`, par défaut). Au premier démarrage, il crée les tables et charge **les mêmes données de démonstration** qu'avec H2 (même générateur, mêmes véhicules, comptes et scénario). Les données sont ensuite **conservées** d'un démarrage à l'autre.

**1. Lancer PostgreSQL**
- Avec Docker : `docker compose up -d postgres` (base `autotrack`, utilisateur `autotrack` / `autotrack`, port 5432).
- Sans Docker (PostgreSQL installé sur le poste) : `psql -U postgres -f backend/db/creer-base.sql`.

**2. Démarrer le backend** (dossier `backend/`)

| Besoin | Windows | Commande équivalente |
|---|---|---|
| Démarrage normal (données conservées) | `demarrer.cmd` | `mvn spring-boot:run` |
| Remettre la démo à zéro (efface et recharge) | `reinitialiser-demo.cmd` | `DDL_AUTO=create mvn spring-boot:run` |
| Démo sans PostgreSQL (H2 en mémoire) | `demarrer-h2.cmd` | `mvn spring-boot:run -Dspring-boot.run.profiles=h2` |

Autres identifiants (ex. compte `postgres` d'un PostgreSQL déjà installé) : copier `backend/config/application-postgres.yml.exemple` en `backend/config/application-postgres.yml` et y mettre l'URL, l'utilisateur et le mot de passe (fichier local, non versionné), ou utiliser les variables `DB_URL`, `DB_USER`, `DB_PASSWORD`. Port 5432 déjà pris par un PostgreSQL Windows : `set DB_PORT=5433` avant `docker compose up -d postgres`, puis `DB_URL=jdbc:postgresql://localhost:5433/autotrack`.

Fichiers de configuration (`backend/src/main/resources/`) : `application.yml` (commun), `application-postgres.yml` (base PostgreSQL), `application-h2.yml` (base en mémoire, démo et tests). Les tests (`mvn test`) utilisent H2 et n'ont pas besoin de PostgreSQL.

> Les dates de la démo sont calculées au moment du chargement (débarquements « d'hier », tickets valables 48 h…). Après quelques jours, lancer `reinitialiser-demo.cmd` pour retrouver le scénario de démonstration tel quel.

## 8. Limites connues du prototype
- Code livré **non compilé dans cet environnement** (pas d'accès aux dépôts Maven/npm/pub) : lancer `mvn test`, `npm install && npm run build` et `flutter analyze` au premier démarrage.
- SMS/e-mails simulés (`GET /api/park/notifications`) ; photos stockées sur disque local (`backend/data/photos`).
- RFID : les lectures de portiques sont simulées (saisie d'un EPC `E280-…` ou d'un VIN). La RFID passive donne des lectures à des points de passage, pas une position continue.
