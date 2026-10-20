import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';
import { TokenResponse, UserInfo } from './models';
import { ROLE_LABELS, canAccess, hasPrivilege, homeFor } from './permissions';

const ACCESS = 'dat.access';
const REFRESH = 'dat.refresh';
const USER = 'dat.user';

/**
 * Gestion de la session JWT.
 * Les jetons sont gardés en sessionStorage (effacés à la fermeture de l'onglet).
 * Pour la production, préférer un cookie HttpOnly pour le jeton de rafraîchissement.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  // Session enregistrée avant l'arrivée des privilèges : on repart d'une connexion propre.
  private readonly _user = signal<UserInfo | null>(this.initialUser());
  readonly user = this._user.asReadonly();
  readonly isLoggedIn = computed(() => this._user() !== null);
  readonly isSupervisor = computed(() => this._user()?.role === 'SUPERVISEUR');
  readonly roleLabel = computed(() => ROLE_LABELS[this._user()?.role ?? ''] ?? '');
  /** Message affiché sous l'en-tête (ex. accès refusé à une page). */
  readonly notice = signal<string | null>(null);

  /** La page (« /tickets ») est-elle ouverte au profil connecté ? */
  canAccess(path: string): boolean {
    return canAccess(this._user(), path);
  }

  /** Le profil connecté a-t-il ce privilège (ex. « TICKET_EMETTRE ») ? */
  can(privilege: string): boolean {
    return hasPrivilege(this._user(), privilege);
  }

  /** Page d'accueil du profil connecté. */
  home(): string {
    return homeFor(this._user());
  }

  /**
   * Relit l'utilisateur et ses privilèges (modifiés par l'administrateur) : au démarrage,
   * au retour sur l'onglet et régulièrement. Les menus se mettent à jour sans reconnexion.
   */
  syncMe(): void {
    if (!this.accessToken) return;
    this.http.get<UserInfo>('/api/auth/me').subscribe({
      next: (u) => {
        sessionStorage.setItem(USER, JSON.stringify(u));
        this._user.set(u);
      },
      error: () => { /* 401 : l'intercepteur gère le rafraîchissement ou la déconnexion */ },
    });
  }

  get accessToken(): string | null {
    return sessionStorage.getItem(ACCESS);
  }

  login(username: string, password: string): Observable<UserInfo> {
    return this.http.post<TokenResponse>('/api/auth/login', { username, password }).pipe(
      tap((t) => this.store(t)),
      map((t) => t.user),
    );
  }

  refresh(): Observable<TokenResponse> {
    const refreshToken = sessionStorage.getItem(REFRESH) ?? '';
    return this.http.post<TokenResponse>('/api/auth/refresh', { refreshToken }).pipe(tap((t) => this.store(t)));
  }

  /** Déconnexion : le serveur invalide les jetons du compte, puis la session locale est effacée. */
  logout(): void {
    if (this.accessToken && this._user()) {
      this.http.post('/api/auth/logout', {}).subscribe({ error: () => undefined });
    }
    this.clearSession();
  }

  /** Nouveaux jetons (ex. après changement du mot de passe, qui ferme les autres sessions). */
  adopt(t: TokenResponse): void {
    this.store(t);
  }

  /** Le serveur exige le changement du mot de passe provisoire. */
  requirePasswordChange(): void {
    const u = this._user();
    if (u && !u.mustChangePassword) this._user.set({ ...u, mustChangePassword: true });
    this.notice.set('Mot de passe provisoire : choisissez votre mot de passe personnel pour accéder à la plateforme.');
    this.router.navigate(['/compte']);
  }

  private clearSession(): void {
    sessionStorage.removeItem(ACCESS);
    sessionStorage.removeItem(REFRESH);
    sessionStorage.removeItem(USER);
    this._user.set(null);
    this.notice.set(null);
    this.router.navigate(['/login']);
  }

  hasRole(...roles: string[]): boolean {
    const r = this._user()?.role;
    return !!r && roles.includes(r);
  }

  private store(t: TokenResponse): void {
    sessionStorage.setItem(ACCESS, t.accessToken);
    sessionStorage.setItem(REFRESH, t.refreshToken);
    sessionStorage.setItem(USER, JSON.stringify(t.user));
    this._user.set(t.user);
  }

  private initialUser(): UserInfo | null {
    const u = this.read<UserInfo>(USER);
    if (u && !Array.isArray(u.privileges)) {
      sessionStorage.removeItem(ACCESS);
      sessionStorage.removeItem(REFRESH);
      sessionStorage.removeItem(USER);
      return null;
    }
    return u;
  }

  private read<T>(key: string): T | null {
    try {
      const raw = sessionStorage.getItem(key);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
  }
}
