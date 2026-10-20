/**
 * Droits du poste web. Les privilèges de chaque profil sont configurés par l'administrateur
 * (Administration → Profils & privilèges) et renvoyés par le serveur avec l'utilisateur connecté
 * (UserInfo.privileges). Une page « parc » correspond au privilège « PAGE_PARC ».
 * Le serveur applique les mêmes privilèges : masquer un menu n'est jamais la seule protection.
 */
export type Role = 'ADMIN' | 'SUPERVISEUR' | 'AGENT' | 'PORTAIL' | 'CLIENT';

export const STAFF_ROLES: Role[] = ['ADMIN', 'SUPERVISEUR', 'AGENT', 'PORTAIL'];

/** Pages accessibles à tout le personnel connecté, sans privilège. */
const ALWAYS: string[] = ['compte'];

/** Ordre de recherche de la page d'accueil (la première autorisée). */
const HOME_ORDER = ['parc', 'portique', 'vehicules', 'enlevements', 'tickets', 'utilisateurs', 'audit', 'assistant', 'cles', 'compte'];
const PREFERRED: Record<string, string> = { ADMIN: 'utilisateurs', PORTAIL: 'portique', SUPERVISEUR: 'parc', AGENT: 'parc' };

export interface PrivilegeHolder { role: string; privileges?: string[] | null; }

export function pageKey(path: string): string {
  return path.replace(/^\//, '').split(/[/?#]/)[0];
}

export function pagePrivilege(key: string): string {
  return 'PAGE_' + key.toUpperCase();
}

export function hasPrivilege(user: PrivilegeHolder | null | undefined, privilege: string): boolean {
  return !!user && !!user.privileges && user.privileges.includes(privilege);
}

/** path accepte « /tickets », « tickets » ou « /tickets?x=1 ». */
export function canAccess(user: PrivilegeHolder | null | undefined, path: string): boolean {
  if (!user || user.role === 'CLIENT') return false;
  const key = pageKey(path);
  return ALWAYS.includes(key) || hasPrivilege(user, pagePrivilege(key));
}

/** Page d'arrivée après connexion : page préférée du profil si autorisée, sinon la première autorisée. */
export function homeFor(user: PrivilegeHolder | null | undefined): string {
  if (!user) return '/login';
  const pref = PREFERRED[user.role];
  if (pref && canAccess(user, pref)) return '/' + pref;
  const first = HOME_ORDER.find((k) => canAccess(user, k));
  return '/' + (first ?? 'compte');
}

export const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Administrateur',
  SUPERVISEUR: 'Superviseur parc',
  AGENT: 'Agent de parc',
  PORTAIL: 'Agent de portail',
  CLIENT: 'Client',
};

export const ROLE_DESCRIPTIONS: Record<string, string> = {
  ADMIN: 'Gère les comptes, les privilèges des profils et consulte le journal d\'audit.',
  SUPERVISEUR: 'Pilote le parc : écrans d\'exploitation, indicateurs, zones, simulation des formalités.',
  AGENT: 'Terrain : réception, état, stationnement, déplacements, étiquettes et clés (mobile et web).',
  PORTAIL: 'Contrôle de sortie : scan des tickets, décisions au portail, consultation des véhicules et des clés.',
  CLIENT: 'Espace mobile uniquement (BL + code SMS) : suivi de son véhicule et ticket de retrait.',
};
