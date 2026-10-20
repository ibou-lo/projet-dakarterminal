# Dakar AutoTrack — revue de sécurité

**Périmètre** : backend Spring Boot (API), poste web Angular, application Flutter.
**Référentiels** : OWASP ASVS 4.0.3 (niveau 2, applications traitant des données personnelles), OWASP API Security Top 10 (2023), OWASP Top 10 (2021), loi sénégalaise n° 2008-12 sur la protection des données à caractère personnel (CDP).
**Méthode** : relecture du code source et de la configuration, corrections, tests automatisés (`DemoScenarioTest`).
**Limites** : revue de code, sans test d'intrusion, sans analyse de l'infrastructure (serveurs, réseau, sauvegardes) ni des processus. Aucune revue de code ne vaut certification : la conformité se démontre au pilote (test d'intrusion, analyse de dépendances, formalités CDP, procédures).

Légende : ✅ en place · 🟡 partiel / à compléter en production · ❌ à faire avant la production

---

## 1. Synthèse

| Domaine | État | Commentaire |
|---|:-:|---|
| Authentification du personnel | ✅ | BCrypt (coût 12), verrouillage 5 échecs / 15 min, limitation de débit par IP, message générique, mot de passe provisoire à changer, politique 12 caractères |
| Sessions / jetons | ✅ | JWT HS256 signé, émetteur vérifié, accès 60 min, durée absolue 12 h, révocation serveur (déconnexion, mot de passe, désactivation, changement de profil) |
| Contrôle d'accès | ✅ | Privilèges par profil appliqués par le serveur (URL + `@PreAuthorize`), objet client lié au JWT (pas d'IDOR), séparation des tâches administrateur |
| Journalisation | ✅ | Journal d'audit serveur non modifiable : connexions, échecs, verrouillages, refus d'accès, comptes, privilèges, actions métier ; conservation 365 j |
| Validation des entrées | ✅ | DTO validés (`@Valid`), JPA paramétré (pas d'injection SQL), fichiers : type, taille, chemin contrôlés |
| Configuration | 🟡 | Secrets de démo refusés si `AUTOTRACK_PRODUCTION=true` ; HTTPS, secrets et désactivation de la doc API à fournir par l'exploitation |
| Transport | ❌ (démo) | HTTP sur réseau local pour la démo ; HTTPS obligatoire en production (web et mobile) |
| Données personnelles (CDP) | 🟡 | Minimisation et masquage en place ; déclaration CDP, information des personnes, chiffrement des sauvegardes à organiser |
| Dépendances | 🟡 | Analyse OWASP Dependency-Check disponible (`mvn -Psecurite verify`), `npm audit` ; à intégrer à la chaîne CI |

---

## 2. OWASP API Security Top 10 (2023)

| Risque | État | Mesures dans le code |
|---|:-:|---|
| API1 Autorisation au niveau objet (BOLA) | ✅ | Client : le BL vient du JWT, jamais d'un paramètre (`ClientController`). Personnel : données d'exploitation partagées par conception, écritures contrôlées par privilège |
| API2 Authentification défaillante | ✅ | Verrouillage de compte, limitation de débit (`LoginRateLimitFilter`), jetons courts + révocation, durée absolue, pas d'énumération |
| API3 Autorisation au niveau propriété | ✅ | DTO dédiés en entrée (pas d'affectation de masse d'entité) ; masquage du nom du bénéficiaire et de la CNI en sortie |
| API4 Consommation illimitée de ressources | 🟡 | Débit limité sur l'authentification, photos ≤ 8 Mo, recherches bornées (audit ≤ 2 000, listes ≤ 300) ; limitation globale à mettre sur la passerelle / reverse proxy |
| API5 Autorisation au niveau fonction | ✅ | 29 privilèges vérifiés par le serveur ; privilèges d'administration verrouillés pour l'administrateur |
| API6 Accès illimité aux flux métier sensibles | ✅ | Ticket signé HMAC, usage unique, expiration 48 h ; sortie validée par un agent ; actions IA confirmées par un humain |
| API7 SSRF | ✅ | Aucune requête sortante construite à partir d'une entrée utilisateur |
| API8 Mauvaise configuration | 🟡 | En-têtes de sécurité, CORS restreint, erreurs sans pile technique, contrôle de démarrage ; H2 et Swagger à désactiver en production |
| API9 Inventaire des API | 🟡 | OpenAPI généré ; tableau des routes dans `docs/ARCHITECTURE.md` ; versionner l'API au pilote |
| API10 Consommation non sûre d'API tierces | ➖ | Pas encore d'intégration (formalités, SMS, systèmes du terminal simulés) : valider et authentifier ces flux lors de l'intégration |

## 3. OWASP ASVS 4.0.3 — contrôles principaux (niveau 2)

| Exigence | État | Détail |
|---|:-:|---|
| V2.1.1 / 2.1.2 longueur du mot de passe (≥ 12, ≤ 128) | ✅ | `AdminService.checkPasswordPolicy` |
| V2.1.7 mots de passe compromis / courants | 🟡 | Liste de mots courants et interdiction de l'identifiant ; brancher une liste de mots de passe compromis en production |
| V2.2.1 anti-automatisation | ✅ | Verrouillage + limitation de débit par IP |
| V2.3.1 mot de passe initial provisoire | ✅ | Changement obligatoire à la première connexion (serveur bloque tout le reste) |
| V2.4.1 stockage des mots de passe | ✅ | BCrypt coût 12 |
| V2.5 / V2.7 récupération, MFA | ❌ | Réinitialisation par l'administrateur uniquement ; **authentification à deux facteurs recommandée pour l'administrateur et le superviseur** |
| V3.3.1 déconnexion invalide la session | ✅ | `POST /api/auth/logout` (version de session incrémentée) |
| V3.3.2 durée absolue de session | ✅ | 12 h depuis la connexion (claim `auth_time`) |
| V3.3.3 fin des autres sessions au changement de mot de passe | ✅ | |
| V3.5.3 jetons signés, algorithme imposé | ✅ | HS256, clé ≥ 32 octets, émetteur vérifié |
| V4.1.1 contrôle d'accès côté serveur | ✅ | |
| V4.1.3 moindre privilège | ✅ | Privilèges configurables, valeurs par défaut restreintes |
| V4.3.1 administration protégée | 🟡 | Privilèges dédiés et journalisés ; ajouter MFA et restriction réseau de l'administration |
| V5 validation, encodage | ✅ | Validation Bean Validation, JPA paramétré, Angular échappe les sorties |
| V7.1 pas de données sensibles dans les journaux | ✅ | Aucun mot de passe ni jeton journalisé |
| V7.2 journalisation des événements de sécurité | ✅ | |
| V7.4 erreurs génériques | ✅ | `server.error.include-stacktrace: never` |
| V8.3 données sensibles dans le navigateur | 🟡 | Jetons en `sessionStorage` (effacés à la fermeture) ; en production, jeton de rafraîchissement en cookie `HttpOnly; Secure; SameSite=Strict` |
| V9.1 TLS partout | ❌ (démo) | À imposer en production (reverse proxy, HSTS déjà émis en HTTPS) |
| V12.3 / 12.4 fichiers téléversés | ✅ | Liste blanche JPEG/PNG, 8 Mo, nom généré, anti-traversée de chemin, hors racine web |
| V14.2 composants vulnérables | 🟡 | `mvn -Psecurite verify` (échec si CVSS ≥ 7), `npm audit` |
| V14.4 en-têtes HTTP | ✅ | CSP `default-src 'none'` sur l'API, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, HSTS |

## 4. Loi n° 2008-12 (CDP) — données personnelles

Données traitées : noms (agents, bénéficiaires), suffixe de CNI, téléphone et e-mail du personnel, photos de véhicules, adresses IP (audit).

| Principe | État | Mesures / actions |
|---|:-:|---|
| Formalités préalables | ❌ | Déclaration du traitement à la Commission de protection des données personnelles (CDP) avant la mise en service |
| Finalité, minimisation | ✅ | Seuls le nom masqué et 4 chiffres de CNI sont exposés au portail et au client |
| Durée de conservation | 🟡 | Audit : 365 j (`AUDIT_RETENTION_DAYS`) ; fixer la durée des passeports, photos et comptes clients avec le terminal |
| Sécurité et confidentialité | 🟡 | Contrôle d'accès, audit, mots de passe hachés ; chiffrer disques/sauvegardes, HTTPS, stockage objet chiffré pour les photos |
| Droits des personnes, information | ❌ | Mentions d'information (espace client, affichage au terminal), procédure d'accès / rectification |
| Transferts hors Sénégal | ➖ | Aucun dans le prototype ; à vérifier si hébergement cloud ou IA externe |

## 5. Corrections apportées lors de cette revue

1. **Révocation des sessions** : version de session par compte ; déconnexion serveur, changement / réinitialisation du mot de passe et désactivation coupent immédiatement les jetons.
2. **Durée absolue de session** de 12 h (plus de rafraîchissement perpétuel).
3. **Mot de passe provisoire** à changer obligatoirement (serveur, web, mobile) ; politique portée à 12 caractères, sans identifiant ni mot courant.
4. **Limitation de débit** par IP sur la connexion (personnel et client) et le rafraîchissement, journalisée.
5. **Verrouillage client** de 15 min au lieu d'un blocage permanent jusqu'au redémarrage.
6. **Anti-énumération** : un compte désactivé reçoit le même message qu'un mauvais mot de passe.
7. **En-têtes de sécurité** : CSP stricte sur l'API, anti-cadrage (sauf console H2 en démo), Referrer-Policy, Permissions-Policy, HSTS.
8. **Contrôle de démarrage** : en production (`AUTOTRACK_PRODUCTION=true`), refus des secrets de démo, du secret de ticket trop court, de la console H2 et des données de démo.
9. **Erreurs** sans détail technique ; documentation API désactivable (`API_DOCS=false`).
10. **Conservation de l'audit** : purge nocturne au-delà de 365 jours.
11. **Mobile** : HTTP non chiffré désactivable (`autotrack.allowHttp=false` dans `android/gradle.properties`) ; déconnexion serveur.
12. **Correctif web** : les appels `/api/auth/me` et `/api/auth/password` partaient sans jeton (menus non rafraîchis, changement de mot de passe impossible).
13. **Analyse des dépendances** : profil Maven `securite` (OWASP Dependency-Check).

## 6. Avant la mise en production (checklist)

- [ ] HTTPS de bout en bout (reverse proxy TLS 1.2+), `autotrack.allowHttp=false`, `AUTOTRACK_ALLOWED_ORIGINS` limité au domaine réel
- [ ] `AUTOTRACK_PRODUCTION=true`, `AUTOTRACK_JWT_SECRET` et `AUTOTRACK_TICKET_SECRET` générés (≥ 32 caractères aléatoires) et stockés dans un coffre de secrets
- [ ] `DEMO_SEED=false`, `API_DOCS=false`, base PostgreSQL avec un compte applicatif aux droits limités, migrations versionnées (Flyway)
- [ ] Authentification à deux facteurs pour les profils Administrateur et Superviseur ; administration accessible depuis le réseau interne uniquement
- [ ] Jeton de rafraîchissement en cookie `HttpOnly`, CSP sur le serveur web Angular
- [ ] OTP client à 6 chiffres, à usage unique et expirant (remplace le code SMS fixe de démo)
- [ ] Limitation de débit globale et WAF sur la passerelle ; supervision des journaux (SIEM) et alertes sur verrouillages / refus
- [ ] `mvn -Psecurite verify` et `npm audit` dans la CI ; test d'intrusion avant ouverture
- [ ] Chiffrement des disques, des sauvegardes et du stockage des photos ; plan de sauvegarde / restauration testé
- [ ] Déclaration CDP, mentions d'information, durées de conservation validées, procédure de gestion des incidents
