import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AdminOverview, AdminUser, UserForm } from '../core/models';
import { ROLE_LABELS, STAFF_ROLES } from '../core/permissions';
import { ZoneStore } from '../core/zone.store';

type StatusFilter = '' | 'active' | 'disabled' | 'locked';

/** Administration des comptes du personnel. */
@Component({
  selector: 'dat-utilisateurs',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow">
          <h1>Utilisateurs</h1>
          <div class="sub">Comptes du personnel du terminal : profils, zones, accès et sécurité</div>
        </div>
        <a class="btn" routerLink="/audit">Journal d'audit</a>
        <button type="button" class="btn primary" (click)="openCreate()">+ Nouvel utilisateur</button>
      </div>

      @if (overview(); as o) {
        <div class="tiles">
          <div class="tile"><span class="label">Comptes</span><span class="value">{{ o.users }}</span>
            <span class="hint">{{ o.activeUsers }} actifs</span></div>
          <div class="tile"><span class="label">Comptes verrouillés</span><span class="value" [class.warn]="o.lockedUsers > 0">{{ o.lockedUsers }}</span>
            <span class="hint">après 5 échecs de connexion</span></div>
          <div class="tile"><span class="label">Connexions · 24 h</span><span class="value">{{ o.logins24h }}</span>
            <span class="hint">{{ o.failures24h }} échec(s)</span></div>
          <div class="tile"><span class="label">Accès refusés · 24 h</span><span class="value" [class.warn]="o.denied24h > 0">{{ o.denied24h }}</span>
            <span class="hint">{{ o.changes7d }} modification(s) de comptes · 7 j</span></div>
        </div>
      }

      <section class="card col">
        <div class="row toolbar">
          <label class="sr-only" for="uq">Rechercher</label>
          <input id="uq" class="field grow" placeholder="Nom, identifiant, e-mail…" [ngModel]="q()" (ngModelChange)="q.set($event)">
          <select class="field" aria-label="Profil" [ngModel]="roleFilter()" (ngModelChange)="roleFilter.set($event)">
            <option value="">Tous les profils</option>
            @for (r of roles; track r) { <option [value]="r">{{ label(r) }}</option> }
          </select>
          <select class="field" aria-label="Statut" [ngModel]="statusFilter()" (ngModelChange)="statusFilter.set($event)">
            <option value="">Tous les statuts</option>
            <option value="active">Actifs</option>
            <option value="locked">Verrouillés</option>
            <option value="disabled">Désactivés</option>
          </select>
          <span class="count">{{ filtered().length }} / {{ users().length }}</span>
        </div>

        @if (message(); as m) {
          <div class="banner" [class.ok]="m.ok" [class.danger]="!m.ok" role="status">{{ m.text }}</div>
        }

        <div class="scroll">
          <table class="data">
            <thead><tr>
              <th>Utilisateur</th><th>Profil</th><th>Zone</th><th>Contact</th><th>Dernière connexion</th><th>Statut</th><th class="right">Actions</th>
            </tr></thead>
            <tbody>
              @for (u of filtered(); track u.username) {
                <tr [class.off]="!u.enabled">
                  <td>
                    <div class="user">
                      <span class="av" [attr.data-role]="u.role">{{ initials(u.fullName) }}</span>
                      <div><strong>{{ u.fullName }}</strong>@if (u.username === me()) { <span class="me">vous</span> }
                        <div class="mono muted">{{ u.username }}</div></div>
                    </div>
                  </td>
                  <td><span class="role" [attr.data-role]="u.role">{{ label(u.role) }}</span></td>
                  <td>{{ u.zone || '—' }}</td>
                  <td class="small">{{ u.email || '—' }}@if (u.phone) { <div class="muted">{{ u.phone }}</div> }</td>
                  <td class="small">
                    @if (u.lastLoginAt) { {{ u.lastLoginAt | date: 'dd/MM/yyyy · HH:mm' }} } @else { <span class="muted">jamais</span> }
                    <div class="muted">créé le {{ u.createdAt | date: 'dd/MM/yyyy' }}@if (u.createdBy) { · {{ u.createdBy }} }</div>
                  </td>
                  <td>
                    @if (!u.enabled) { <span class="st off">Désactivé</span> }
                    @else if (u.locked) { <span class="st lock" [title]="'Verrouillé jusqu\\'à ' + (u.lockedUntil | date: 'HH:mm')">Verrouillé</span> }
                    @else { <span class="st on">Actif</span> }
                    @if (u.failedAttempts > 0 && !u.locked) { <div class="muted small">{{ u.failedAttempts }} échec(s)</div> }
                  </td>
                  <td class="right actions">
                    <button type="button" class="btn sm" (click)="openEdit(u)">Modifier</button>
                    <button type="button" class="btn sm" (click)="reset(u)">Mot de passe</button>
                    @if (u.locked) { <button type="button" class="btn sm" (click)="act(u, 'unlock')">Déverrouiller</button> }
                    @if (u.enabled) {
                      <button type="button" class="btn sm danger" [disabled]="u.username === me()" (click)="act(u, 'disable')">Désactiver</button>
                    } @else {
                      <button type="button" class="btn sm" (click)="act(u, 'enable')">Réactiver</button>
                    }
                    <a class="btn sm ghost" routerLink="/audit" [queryParams]="{ actor: u.username }" title="Activité de ce compte">Activité</a>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="muted empty">Aucun utilisateur ne correspond aux filtres.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <!-- Création / modification -->
    @if (form(); as f) {
      <div class="backdrop" (click)="form.set(null)" role="presentation">
        <form class="dlg" role="dialog" aria-modal="true" aria-labelledby="uf-title" (click)="$event.stopPropagation()" (ngSubmit)="save()" #uf="ngForm">
          <header class="row">
            <h2 id="uf-title">{{ editing() ? 'Modifier ' + editing() : 'Nouvel utilisateur' }}</h2>
            <span class="grow"></span>
            <button type="button" class="x" aria-label="Fermer" (click)="form.set(null)">✕</button>
          </header>
          @if (!editing()) {
            <label>Identifiant de connexion
              <input class="field mono" name="username" [(ngModel)]="f.username" required pattern="[a-zA-Z0-9._-]{3,40}" placeholder="prenom.nom">
              <span class="help">3 à 40 caractères : lettres, chiffres, point, tiret</span>
            </label>
          }
          <label>Nom affiché
            <input class="field" name="fullName" [(ngModel)]="f.fullName" required maxlength="120" placeholder="A. Diallo">
          </label>
          <div class="grid2">
            <label>Profil
              <select class="field" name="role" [(ngModel)]="f.role" required [disabled]="editing() === me()">
                @for (r of roles; track r) { <option [value]="r">{{ label(r) }}</option> }
              </select>
            </label>
            <label>Zone d'affectation
              <select class="field" name="zone" [(ngModel)]="f.zone">
                <option [ngValue]="null">—</option>
                @for (z of zoneStore.codes(); track z) { <option [ngValue]="z">Zone {{ z }}</option> }
              </select>
            </label>
          </div>
          <p class="roleinfo">{{ roleInfo(f.role) }}</p>
          <div class="grid2">
            <label>E-mail <input class="field" type="email" name="email" [(ngModel)]="f.email" maxlength="120"></label>
            <label>Téléphone <input class="field" name="phone" [(ngModel)]="f.phone" maxlength="30"></label>
          </div>
          @if (!editing()) {
            <p class="help">Un mot de passe provisoire est généré et affiché une seule fois à la création.</p>
          }
          @if (formError()) { <div class="banner danger" role="alert">{{ formError() }}</div> }
          <div class="row end">
            <button type="button" class="btn" (click)="form.set(null)">Annuler</button>
            <button type="submit" class="btn primary" [disabled]="!uf.valid || busy()">{{ editing() ? 'Enregistrer' : 'Créer le compte' }}</button>
          </div>
        </form>
      </div>
    }

    <!-- Mot de passe provisoire -->
    @if (tempPassword(); as t) {
      <div class="backdrop" role="presentation">
        <section class="dlg small-dlg" role="dialog" aria-modal="true" aria-labelledby="tp-title">
          <h2 id="tp-title">Mot de passe provisoire</h2>
          <p>À remettre à <strong>{{ t.name }}</strong> ({{ t.username }}). Il ne sera plus affiché ensuite.</p>
          <div class="pwd mono">{{ t.password }}</div>
          <p class="help">Il devra le remplacer par son mot de passe personnel dès sa première connexion.</p>
          <div class="row end">
            <button type="button" class="btn" (click)="copy(t.password)">{{ copied() ? 'Copié ✓' : 'Copier' }}</button>
            <button type="button" class="btn primary" (click)="tempPassword.set(null)">J'ai noté le mot de passe</button>
          </div>
        </section>
      </div>
    }
  `,
  styles: [`
    .tile .hint { font-size: 12px; color: var(--muted); }
    .tile .value.warn { color: var(--warn); }
    .toolbar { flex-wrap: wrap; }
    .count { font-size: 13px; color: var(--muted); }
    .scroll { overflow-x: auto; }
    .user { display: flex; align-items: center; gap: 10px; }
    .av { width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; background: var(--ground-2); color: var(--ink); flex: none; }
    .av[data-role='ADMIN'] { background: var(--ink); color: #fff; }
    .av[data-role='SUPERVISEUR'] { background: #fbe0cc; color: var(--rust); }
    .me { margin-left: 6px; font-size: 11px; padding: 1px 6px; border-radius: 999px; background: var(--info-bg); color: var(--info-ink); }
    .mono { font-family: var(--mono); font-size: 12px; }
    .muted { color: var(--muted); }
    .small { font-size: 12px; }
    .role { font-size: 12px; font-weight: 600; padding: 3px 9px; border-radius: 999px; background: var(--ground-2); white-space: nowrap; }
    .role[data-role='ADMIN'] { background: var(--ink); color: #fff; }
    .role[data-role='SUPERVISEUR'] { background: #fbe0cc; color: var(--rust); }
    .role[data-role='PORTAIL'] { background: var(--info-bg); color: var(--info-ink); }
    .st { font-size: 12px; font-weight: 600; padding: 3px 9px; border-radius: 999px; }
    .st.on { background: var(--ok-bg); color: var(--ok-ink); }
    .st.off { background: var(--ground-2); color: var(--muted); }
    .st.lock { background: var(--warn-bg); color: var(--warn-ink); }
    tr.off td { color: var(--muted); }
    .right { text-align: right; }
    .actions { white-space: nowrap; }
    .actions .btn { margin-left: 4px; }
    .ghost { border-color: transparent; }
    .empty { text-align: center; padding: 28px; }
    .backdrop { position: fixed; inset: 0; background: rgba(15, 29, 43, .55); display: flex; align-items: center; justify-content: center; z-index: 100; padding: 16px; }
    .dlg { width: 560px; max-width: 100%; max-height: 100%; overflow: auto; background: #fff; border-radius: 18px; padding: 22px; display: flex; flex-direction: column; gap: 12px; box-shadow: 0 20px 60px rgba(0,0,0,.3); }
    .small-dlg { width: 440px; }
    .dlg h2 { font-family: var(--display); font-size: 22px; font-weight: 800; }
    .dlg label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; color: var(--text-2); }
    .dlg .field { width: 100%; }
    .grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .help { font-size: 12px; font-weight: 400; color: var(--muted); margin: 0; }
    .roleinfo { font-size: 13px; background: var(--ground); border-radius: 10px; padding: 10px 12px; margin: 0; color: var(--text-2); }
    .x { border: 0; background: transparent; font-size: 18px; cursor: pointer; width: 36px; height: 36px; border-radius: 8px; }
    .end { justify-content: flex-end; }
    .pwd { font-size: 22px; font-weight: 700; letter-spacing: 1px; text-align: center; padding: 16px; border: 2px dashed var(--line-2); border-radius: 12px; background: var(--ground); user-select: all; }
  `],
})
export class UtilisateursComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  zoneStore = inject(ZoneStore);

  readonly roles = STAFF_ROLES;
  users = signal<AdminUser[]>([]);
  overview = signal<AdminOverview | null>(null);
  q = signal('');
  roleFilter = signal('');
  statusFilter = signal<StatusFilter>('');
  message = signal<{ ok: boolean; text: string } | null>(null);
  form = signal<UserForm | null>(null);
  editing = signal<string | null>(null);
  formError = signal<string | null>(null);
  busy = signal(false);
  tempPassword = signal<{ username: string; name: string; password: string } | null>(null);
  copied = signal(false);
  me = computed(() => this.auth.user()?.username ?? '');

  filtered = computed(() => {
    const q = this.q().trim().toLowerCase();
    const r = this.roleFilter();
    const st = this.statusFilter();
    return this.users().filter((u) =>
      (!q || [u.username, u.fullName, u.email ?? '', u.phone ?? ''].some((x) => x.toLowerCase().includes(q)))
      && (!r || u.role === r)
      && (!st || (st === 'active' && u.enabled && !u.locked) || (st === 'disabled' && !u.enabled) || (st === 'locked' && u.locked)));
  });

  ngOnInit(): void {
    this.zoneStore.load();
    this.reload();
  }

  reload(): void {
    this.api.adminUsers().subscribe((u) => this.users.set(u));
    this.api.adminOverview().subscribe((o) => this.overview.set(o));
  }

  label(role: string): string {
    return ROLE_LABELS[role] ?? role;
  }

  roleInfo(role: string): string {
    return ({
      ADMIN: 'Administrateur : comptes et journal d\'audit. Aucun accès aux opérations du parc.',
      SUPERVISEUR: 'Superviseur : tous les écrans d\'exploitation, indicateurs, zones, audit en lecture.',
      AGENT: 'Agent de parc : réception, stationnement, déplacements, étiquettes, clés (mobile et web).',
      PORTAIL: 'Agent de portail : contrôle de sortie, tickets, consultation des véhicules et des clés.',
    } as Record<string, string>)[role] ?? '';
  }

  initials(name: string): string {
    return name.split(/[\s.]+/).filter(Boolean).map((p) => p[0]).join('').slice(0, 2).toUpperCase();
  }

  openCreate(): void {
    this.editing.set(null);
    this.formError.set(null);
    this.form.set({ username: '', fullName: '', role: 'AGENT', zone: 'B', email: null, phone: null });
  }

  openEdit(u: AdminUser): void {
    this.editing.set(u.username);
    this.formError.set(null);
    this.form.set({ fullName: u.fullName, role: u.role, zone: u.zone, email: u.email, phone: u.phone });
  }

  save(): void {
    const f = this.form();
    if (!f) return;
    this.busy.set(true);
    this.formError.set(null);
    const edit = this.editing();
    const done = (text: string) => {
      this.busy.set(false);
      this.form.set(null);
      this.message.set({ ok: true, text });
      this.reload();
    };
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const fail = (e: any) => {
      this.busy.set(false);
      this.formError.set(e?.error?.message || 'Enregistrement impossible');
    };
    if (edit) {
      this.api.updateUser(edit, f).subscribe({ next: (u) => done(`Compte ${u.username} mis à jour.`), error: fail });
    } else {
      this.api.createUser(f).subscribe({
        next: (r) => {
          done(`Compte ${r.user.username} créé.`);
          this.showPassword(r.user.username, r.user.fullName, r.temporaryPassword);
        },
        error: fail,
      });
    }
  }

  act(u: AdminUser, action: 'enable' | 'disable' | 'unlock'): void {
    if (action === 'disable' && !window.confirm(`Désactiver le compte ${u.username} ? Ses sessions en cours seront coupées.`)) return;
    const labels = { enable: 'réactivé', disable: 'désactivé', unlock: 'déverrouillé' };
    this.api.userAction(u.username, action).subscribe({
      next: () => { this.message.set({ ok: true, text: `Compte ${u.username} ${labels[action]}.` }); this.reload(); },
      error: (e) => this.message.set({ ok: false, text: e?.error?.message || 'Action impossible' }),
    });
  }

  reset(u: AdminUser): void {
    if (!window.confirm(`Générer un nouveau mot de passe provisoire pour ${u.username} ? L'ancien ne fonctionnera plus.`)) return;
    this.api.resetPassword(u.username).subscribe({
      next: (r) => { this.showPassword(u.username, u.fullName, r.temporaryPassword); this.reload(); },
      error: (e) => this.message.set({ ok: false, text: e?.error?.message || 'Réinitialisation impossible' }),
    });
  }

  private showPassword(username: string, name: string, password: string): void {
    this.copied.set(false);
    this.tempPassword.set({ username, name, password });
  }

  copy(text: string): void {
    navigator.clipboard?.writeText(text).then(() => this.copied.set(true)).catch(() => this.copied.set(false));
  }
}
