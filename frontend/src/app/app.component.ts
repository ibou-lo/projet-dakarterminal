import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from './core/auth.service';

interface NavLink { path: string; label: string; hint?: string; }
interface NavGroup { id: string; label: string; path?: string; children?: NavLink[]; }

@Component({
  selector: 'dat-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (auth.isLoggedIn()) {
      <header class="top">
        <a [routerLink]="auth.home()" class="brand" aria-label="Dakar AutoTrack, accueil">
          <svg width="32" height="32" viewBox="0 0 32 32" fill="none" aria-hidden="true"><rect x="1" y="1" width="30" height="30" rx="7" fill="#D9601A"/><path d="M8 19.5l2-5.2c.3-.8 1-1.3 1.9-1.3h8.2c.9 0 1.6.5 1.9 1.3l2 5.2M7.5 19.5h17v3.5h-17z" stroke="#fff" stroke-width="1.8" stroke-linejoin="round"/><circle cx="11" cy="23" r="1.6" fill="#fff"/><circle cx="21" cy="23" r="1.6" fill="#fff"/></svg>
          <span>Dakar AutoTrack</span>
        </a>

        <nav aria-label="Navigation principale">
          @for (g of nav(); track g.id) {
            @if (g.children) {
              <div class="group" (mouseenter)="open.set(g.id)" (mouseleave)="open.set(null)">
                <button type="button" class="tab" [class.active]="isGroupActive(g)" [attr.aria-expanded]="open() === g.id"
                        (click)="toggle(g.id, $event)">
                  {{ g.label }}
                  <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true"><path d="M2 3.5 5 6.5 8 3.5" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>
                </button>
                @if (open() === g.id) {
                  <div class="menu" role="menu">
                    @for (c of g.children; track c.path) {
                      <a role="menuitem" [routerLink]="c.path" routerLinkActive="current" (click)="open.set(null)">
                        <strong>{{ c.label }}</strong>
                        @if (c.hint) { <span>{{ c.hint }}</span> }
                      </a>
                    }
                  </div>
                }
              </div>
            } @else {
              <a class="tab" [routerLink]="g.path" routerLinkActive="active">{{ g.label }}</a>
            }
          }
        </nav>

        <div class="grow"></div>
        <div class="me">
          <div class="avatar" [title]="roleLabel()">{{ initials() }}</div>
          <a class="who" routerLink="/compte" title="Mon compte · mot de passe">
            <strong>{{ auth.user()?.fullName }}</strong>
            <span>{{ roleLabel() }}@if (auth.user()?.zone) { · zone {{ auth.user()?.zone }} }</span>
          </a>
          <button type="button" class="logout" (click)="auth.logout()" aria-label="Déconnexion" title="Déconnexion">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/></svg>
          </button>
        </div>
      </header>
    }
    @if (auth.notice(); as n) {
      <div class="notice" role="alert">
        <span>{{ n }}</span>
        <button type="button" aria-label="Fermer" (click)="auth.notice.set(null)">✕</button>
      </div>
    }
    <main><router-outlet /></main>
  `,
  styles: [`
    .top { height: 64px; background: var(--ink); color: #fff; display: flex; align-items: center; gap: 24px; padding: 0 24px; position: sticky; top: 0; z-index: 10; }
    .brand { display: flex; align-items: center; gap: 12px; color: #fff; text-decoration: none; font-family: var(--display); font-weight: 800; font-size: 19px; white-space: nowrap; }
    nav { display: flex; gap: 2px; align-items: center; }
    .tab { display: inline-flex; align-items: center; gap: 6px; color: var(--on-ink-muted); text-decoration: none; padding: 8px 12px; border-radius: 8px;
           font: 500 14px var(--font); background: none; border: 0; cursor: pointer; white-space: nowrap; }
    .tab:hover { color: #fff; }
    .tab.active { color: #fff; background: var(--ink-3); }
    .group { position: relative; }
    .group::after { content: ''; position: absolute; left: 0; right: 0; top: 100%; height: 10px; }
    .menu { position: absolute; top: calc(100% + 8px); left: 0; min-width: 250px; background: #fff; color: var(--text, #0F1D2B); border-radius: 12px;
            box-shadow: 0 12px 32px rgba(15, 29, 43, .22); padding: 6px; display: flex; flex-direction: column; }
    .group:last-of-type .menu { left: auto; right: 0; }
    .menu a { display: flex; flex-direction: column; gap: 2px; padding: 9px 12px; border-radius: 8px; text-decoration: none; color: inherit; font-size: 14px; }
    .menu a span { font-size: 12px; color: var(--muted, #56606B); }
    .menu a:hover { background: var(--ground, #F3F1EB); }
    .menu a.current { background: #FBE9DD; }
    .menu a.current strong { color: #B94E12; }
    .grow { flex: 1; }
    .notice { display: flex; align-items: center; gap: 12px; background: var(--warn-bg); color: var(--warn-ink); padding: 10px 24px; font-size: 14px; border-bottom: 1px solid #f0d49c; }
    .notice span { flex: 1; }
    .notice button { border: 0; background: none; color: inherit; cursor: pointer; font-size: 16px; width: 30px; height: 30px; border-radius: 6px; }
    .role-chip { font-size: 11px; font-weight: 600; letter-spacing: .3px; padding: 2px 8px; border-radius: 999px; background: var(--ink-3); color: var(--peach); align-self: flex-start; }
    .me { display: flex; align-items: center; gap: 10px; font-size: 13px; }
    .avatar { width: 36px; height: 36px; border-radius: 50%; background: var(--ink-line); display: flex; align-items: center; justify-content: center; font-weight: 600; flex: none; }
    .who { display: flex; flex-direction: column; line-height: 1.25; white-space: nowrap; color: #fff; text-decoration: none; }
    .who:hover strong { text-decoration: underline; }
    .who span { color: var(--on-ink-muted); }
    .logout { background: transparent; border: 1px solid var(--ink-line); color: #fff; border-radius: 8px; width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; cursor: pointer; flex: none; }
    .logout:hover { background: var(--ink-3); }
    @media (max-width: 1180px) { .who { display: none; } .brand span { display: none; } }
    @media (max-width: 820px) { .top { overflow-x: auto; gap: 12px; padding: 0 12px; } }
  `],
})
export class AppComponent {
  auth = inject(AuthService);
  private router = inject(Router);

  open = signal<string | null>(null);

  private url = toSignal(
    this.router.events.pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd), map((e) => e.urlAfterRedirects)),
    { initialValue: this.router.url },
  );

  private readonly groups: NavGroup[] = [
    { id: 'parc', label: 'Parc', path: '/parc' },
    {
      id: 'vehicules', label: 'Véhicules', children: [
        { path: '/vehicules', label: 'Liste des véhicules', hint: 'Recherche, statuts, passeports' },
        { path: '/affectation', label: 'Affectation', hint: 'Placer un véhicule sur le plan' },
        { path: '/mouvements', label: 'Mouvements', hint: 'Déplacements et régularisations' },
      ],
    },
    {
      id: 'sorties', label: 'Sorties', children: [
        { path: '/enlevements', label: 'Enlèvements', hint: 'Tableau formalités → sortie' },
        { path: '/tickets', label: 'Tickets de retrait', hint: 'Tickets émis, utilisés, expirés' },
        { path: '/portique', label: 'Portique', hint: 'Contrôle de sortie et portiques RFID' },
      ],
    },
    {
      id: 'tracabilite', label: 'QR & RFID', children: [
        { path: '/etiquettes', label: 'Étiquettes', hint: 'Étiquettes hybrides et places à imprimer' },
        { path: '/cles', label: 'Armoire à clés', hint: 'Qui détient quelle clé' },
        { path: '/fraicheur', label: 'Fraîcheur des lectures', hint: 'Carte du parc et incohérences' },
      ],
    },
    { id: 'indicateurs', label: 'Indicateurs', path: '/indicateurs' },
    { id: 'assistant', label: 'Assistant IA', path: '/assistant' },
    {
      id: 'administration', label: 'Administration', children: [
        { path: '/utilisateurs', label: 'Utilisateurs', hint: 'Comptes, profils, verrouillages' },
        { path: '/profils', label: 'Profils & privilèges', hint: 'Droits de chaque profil, modifiables' },
        { path: '/audit', label: 'Journal d\'audit', hint: 'Connexions, refus, actions' },
      ],
    },
  ];

  /** Menu filtré selon le profil connecté (matrice PAGE_ACCESS de core/permissions.ts). */
  nav = computed<NavGroup[]>(() => {
    this.auth.user(); // dépendance : recalcul à chaque changement de session
    const ok = (path?: string) => !!path && this.auth.canAccess(path);
    return this.groups
      .map((g) => (g.children ? { ...g, children: g.children.filter((c) => ok(c.path)) } : g))
      .filter((g) => (g.children ? g.children.length > 0 : ok(g.path)));
  });

  isGroupActive(g: NavGroup): boolean {
    const u = this.url();
    return (g.children || []).some((c) => u === c.path || u.startsWith(c.path + '/') || u.startsWith(c.path + '?'));
  }

  toggle(id: string, ev: Event) {
    ev.stopPropagation();
    this.open.set(id); // le survol ouvre déjà le menu : le clic le garde ouvert (utile au tactile)
  }

  constructor() {
    // Privilèges modifiés par l'administrateur : pris en compte sans reconnexion
    this.auth.syncMe();
    setInterval(() => this.auth.syncMe(), 60_000);
  }

  @HostListener('window:focus')
  onFocus() { this.auth.syncMe(); }

  @HostListener('document:click')
  closeMenus() { this.open.set(null); }

  @HostListener('document:keydown.escape')
  onEscape() { this.open.set(null); }

  initials = computed(() => (this.auth.user()?.fullName ?? '?').split(/[\s.]+/).filter(Boolean).map((p) => p[0]).join('').slice(0, 2).toUpperCase());

  roleLabel = this.auth.roleLabel;
}
