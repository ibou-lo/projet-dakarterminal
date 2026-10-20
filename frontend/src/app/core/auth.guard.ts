import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Accès réservé aux personnes connectées, puis contrôle du profil selon la matrice
 * PAGE_ACCESS (core/permissions.ts). Page non autorisée → retour à la page d'accueil du profil.
 */
export const authGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }
  const path = route.routeConfig?.path ?? '';
  if (!path) return true; // conteneur des pages
  // Mot de passe provisoire : seule la page « Mon compte » est ouverte
  if (auth.user()?.mustChangePassword && path !== 'compte') {
    auth.notice.set('Mot de passe provisoire : choisissez votre mot de passe personnel pour accéder à la plateforme.');
    return router.createUrlTree(['/compte']);
  }
  if (auth.canAccess(path)) return true;

  const home = auth.home();
  if (!auth.canAccess(home)) {
    // Profil sans accès au poste web (ex. client) : on ferme la session
    auth.logout();
    return router.createUrlTree(['/login']);
  }
  auth.notice.set(`Accès refusé : la page « ${path} » n'est pas ouverte au profil ${auth.roleLabel()}.`);
  return router.createUrlTree([home]);
};
