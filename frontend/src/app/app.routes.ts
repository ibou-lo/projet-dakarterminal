import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { AuthService } from './core/auth.service';

/** Droits par page : voir core/permissions.ts (PAGE_ACCESS). */
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login.component').then((m) => m.LoginComponent) },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      // Accueil selon le profil : portail → Portique, autres → Parc
      { path: '', pathMatch: 'full', redirectTo: () => inject(AuthService).home().slice(1) },
      { path: 'parc', canActivate: [authGuard], loadComponent: () => import('./pages/parc.component').then((m) => m.ParcComponent) },
      { path: 'vehicules', canActivate: [authGuard], loadComponent: () => import('./pages/vehicules.component').then((m) => m.VehiculesComponent) },
      { path: 'affectation', canActivate: [authGuard], loadComponent: () => import('./pages/affectation.component').then((m) => m.AffectationComponent) },
      { path: 'mouvements', canActivate: [authGuard], loadComponent: () => import('./pages/mouvements.component').then((m) => m.MouvementsComponent) },
      { path: 'enlevements', canActivate: [authGuard], loadComponent: () => import('./pages/enlevements.component').then((m) => m.EnlevementsComponent) },
      { path: 'tickets', canActivate: [authGuard], loadComponent: () => import('./pages/tickets.component').then((m) => m.TicketsComponent) },
      { path: 'portique', canActivate: [authGuard], loadComponent: () => import('./pages/portique.component').then((m) => m.PortiqueComponent) },
      { path: 'etiquettes', canActivate: [authGuard], loadComponent: () => import('./pages/etiquettes.component').then((m) => m.EtiquettesComponent) },
      { path: 'cles', canActivate: [authGuard], loadComponent: () => import('./pages/cles.component').then((m) => m.ClesComponent) },
      { path: 'fraicheur', canActivate: [authGuard], loadComponent: () => import('./pages/fraicheur.component').then((m) => m.FraicheurComponent) },
      { path: 'indicateurs', canActivate: [authGuard], loadComponent: () => import('./pages/indicateurs.component').then((m) => m.IndicateursComponent) },
      { path: 'assistant', canActivate: [authGuard], loadComponent: () => import('./pages/assistant.component').then((m) => m.AssistantComponent) },
      { path: 'utilisateurs', canActivate: [authGuard], loadComponent: () => import('./pages/utilisateurs.component').then((m) => m.UtilisateursComponent) },
      { path: 'profils', canActivate: [authGuard], loadComponent: () => import('./pages/profils.component').then((m) => m.ProfilsComponent) },
      { path: 'audit', canActivate: [authGuard], loadComponent: () => import('./pages/audit.component').then((m) => m.AuditComponent) },
      { path: 'compte', canActivate: [authGuard], loadComponent: () => import('./pages/compte.component').then((m) => m.CompteComponent) },
    ],
  },
  { path: '**', redirectTo: '' },
];
