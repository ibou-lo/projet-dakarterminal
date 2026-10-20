import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { AdminUser, PrivilegeDef, PrivilegeMatrix } from '../core/models';
import { ROLE_DESCRIPTIONS, ROLE_LABELS } from '../core/permissions';

interface Group { name: string; items: PrivilegeDef[]; }

/**
 * Profils & privilèges : l'administrateur coche, pour chaque profil, les pages et les actions autorisées.
 * Les modifications sont enregistrées ensemble, journalisées, et appliquées par le serveur dès la requête suivante.
 */
@Component({
  selector: 'dat-profils',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow">
          <h1>Profils & privilèges</h1>
          <div class="sub">Cochez ce que chaque profil peut voir et faire. Appliqué par le serveur, sans reconnexion des utilisateurs.</div>
        </div>
        @if (editable()) {
          <button type="button" class="btn" (click)="resetDefaults()" [disabled]="busy()">Rétablir les valeurs par défaut</button>
          @if (changes() > 0) { <button type="button" class="btn" (click)="discard()">Annuler</button> }
          <button type="button" class="btn primary" (click)="save()" [disabled]="changes() === 0 || busy()">
            Enregistrer @if (changes() > 0) { ({{ changes() }}) }
          </button>
        }
      </div>

      @if (message(); as m) { <div class="banner" [class.ok]="m.ok" [class.danger]="!m.ok" role="status">{{ m.text }}</div> }
      @if (!editable()) { <div class="banner info">Consultation seule : la modification demande le privilège « Gérer les privilèges des profils ».</div> }

      @if (matrix(); as mx) {
        <div class="roles">
          @for (r of mx.roles; track r) {
            <section class="card role" [attr.data-role]="r">
              <div class="row"><h2>{{ label(r) }}</h2><span class="grow"></span><span class="n">{{ countUsers(r) }} compte(s)</span></div>
              <p>{{ desc(r) }}</p>
              <div class="meta"><strong>{{ countGranted(r, 'PAGE') }}</strong> pages · <strong>{{ countGranted(r, 'ACTION') }}</strong> actions</div>
            </section>
          }
        </div>

        <div class="tabs" role="tablist">
          <button type="button" role="tab" [attr.aria-selected]="tab() === 'PAGE'" [class.on]="tab() === 'PAGE'" (click)="tab.set('PAGE')">Pages du poste web</button>
          <button type="button" role="tab" [attr.aria-selected]="tab() === 'ACTION'" [class.on]="tab() === 'ACTION'" (click)="tab.set('ACTION')">Actions</button>
          <span class="grow"></span>
          <span class="legend"><i class="lg def"></i>différent de la valeur par défaut <i class="lg chg"></i>modification non enregistrée 🔒 non modifiable</span>
        </div>

        <section class="card flush">
          <div class="scroll">
            <table class="data matrix">
              <thead>
                <tr>
                  <th>{{ tab() === 'PAGE' ? 'Page' : 'Action' }}</th>
                  @for (r of mx.roles; track r) { <th class="c">{{ label(r) }}</th> }
                </tr>
              </thead>
              <tbody>
                @for (g of groups(); track g.name) {
                  <tr class="grp"><td [attr.colspan]="mx.roles.length + 1">{{ g.name }}</td></tr>
                  @for (p of g.items; track p.code) {
                    <tr>
                      <td>
                        <strong>{{ p.label }}</strong>
                        <div class="muted small">{{ p.description }}</div>
                      </td>
                      @for (r of mx.roles; track r) {
                        <td class="c" [class.changed]="isChanged(r, p.code)" [class.custom]="!isChanged(r, p.code) && differsFromDefault(r, p)">
                          @if (p.locked.includes(r)) {
                            <span class="lock" title="Privilège indispensable à ce profil">🔒</span>
                          } @else {
                            <label class="sw" [attr.aria-label]="label(r) + ' · ' + p.label">
                              <input type="checkbox" [checked]="granted(r, p.code)" [disabled]="!editable()" (change)="toggle(r, p.code)">
                              <span></span>
                            </label>
                          }
                        </td>
                      }
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        </section>

        <p class="muted small">
          @if (mx.updatedAt) { Dernière modification le {{ mx.updatedAt | date: 'dd/MM/yyyy à HH:mm' }} par {{ mx.updatedBy }} · }
          {{ mx.customSettings }} réglage(s) personnalisé(s) ·
          <a routerLink="/audit" [queryParams]="{ action: 'PRIVILEGES_MODIFIES' }">historique dans le journal d'audit</a>.
          Les données communes (parc, véhicules, zones) restent lisibles par tout le personnel ; les pages et actions ci-dessus sont contrôlées par le serveur.
        </p>
      } @else {
        <p class="muted">Chargement…</p>
      }
    </div>
  `,
  styles: [`
    .roles { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); gap: 16px; }
    .role { display: flex; flex-direction: column; gap: 6px; border-top: 4px solid var(--line-2); }
    .role[data-role='ADMIN'] { border-top-color: var(--ink); }
    .role[data-role='SUPERVISEUR'] { border-top-color: var(--rust); }
    .role[data-role='AGENT'] { border-top-color: var(--ok); }
    .role[data-role='PORTAIL'] { border-top-color: var(--info-ink); }
    .role p { margin: 0; color: var(--text-2); font-size: 13px; line-height: 1.45; }
    .meta { font-size: 12px; color: var(--muted); }
    .n { font-size: 12px; font-weight: 600; background: var(--ground-2); padding: 3px 9px; border-radius: 999px; }
    .tabs { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
    .tabs button { border: 1.5px solid var(--line-2); background: #fff; border-radius: 999px; padding: 7px 16px; font: inherit; font-weight: 600; cursor: pointer; }
    .tabs button.on { background: var(--ink); border-color: var(--ink); color: #fff; }
    .legend { font-size: 12px; color: var(--muted); display: flex; align-items: center; gap: 6px; }
    .lg { display: inline-block; width: 12px; height: 12px; border-radius: 3px; margin-left: 8px; }
    .lg.def { background: #fdf3ec; border: 1px solid #f0c9a8; }
    .lg.chg { background: #fff3c4; border: 1px solid #e8c547; }
    .flush { padding: 0; overflow: hidden; }
    .scroll { overflow-x: auto; }
    .matrix .c { text-align: center; width: 150px; }
    .matrix td.custom { background: #fdf3ec; }
    .matrix td.changed { background: #fff3c4; }
    .matrix tr.grp td { background: #f7f5f0; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .4px; color: var(--muted); padding: 6px 12px; }
    .muted { color: var(--muted); }
    .small { font-size: 12px; }
    .lock { font-size: 15px; opacity: .8; }
    /* interrupteur */
    .sw { position: relative; display: inline-block; width: 40px; height: 22px; cursor: pointer; }
    .sw input { opacity: 0; width: 0; height: 0; position: absolute; }
    .sw span { position: absolute; inset: 0; background: #d5cfc2; border-radius: 999px; transition: background .15s; }
    .sw span::after { content: ''; position: absolute; left: 3px; top: 3px; width: 16px; height: 16px; border-radius: 50%; background: #fff; transition: transform .15s; box-shadow: 0 1px 2px rgba(0,0,0,.2); }
    .sw input:checked + span { background: var(--ok); }
    .sw input:checked + span::after { transform: translateX(18px); }
    .sw input:focus-visible + span { outline: 2px solid var(--ink); outline-offset: 2px; }
    .sw input:disabled + span { opacity: .55; cursor: not-allowed; }
  `],
})
export class ProfilsComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);

  matrix = signal<PrivilegeMatrix | null>(null);
  /** Brouillon en cours d'édition : profil → privilèges cochés. */
  draft = signal<Record<string, Set<string>>>({});
  users = signal<AdminUser[]>([]);
  tab = signal<'PAGE' | 'ACTION'>('PAGE');
  busy = signal(false);
  message = signal<{ ok: boolean; text: string } | null>(null);
  editable = computed(() => this.auth.can('PRIVILEGES_GERER'));

  groups = computed<Group[]>(() => {
    const out: Group[] = [];
    for (const p of this.matrix()?.privileges ?? []) {
      if (p.kind !== this.tab()) continue;
      let g = out.find((x) => x.name === p.group);
      if (!g) out.push((g = { name: p.group, items: [] }));
      g.items.push(p);
    }
    return out;
  });

  changes = computed(() => {
    const m = this.matrix();
    if (!m) return 0;
    let n = 0;
    for (const r of m.roles) {
      const saved = new Set(m.grants[r] ?? []);
      const cur = this.draft()[r] ?? new Set<string>();
      for (const p of m.privileges) if (saved.has(p.code) !== cur.has(p.code)) n++;
    }
    return n;
  });

  ngOnInit(): void {
    this.load();
    if (this.auth.can('UTILISATEURS_GERER')) this.api.adminUsers().subscribe((u) => this.users.set(u));
  }

  load(): void {
    this.api.privileges().subscribe((m) => this.apply(m));
  }

  private apply(m: PrivilegeMatrix): void {
    this.matrix.set(m);
    const d: Record<string, Set<string>> = {};
    for (const r of m.roles) d[r] = new Set(m.grants[r] ?? []);
    this.draft.set(d);
  }

  granted(role: string, code: string): boolean {
    return this.draft()[role]?.has(code) ?? false;
  }

  isChanged(role: string, code: string): boolean {
    const saved = this.matrix()?.grants[role] ?? [];
    return saved.includes(code) !== this.granted(role, code);
  }

  differsFromDefault(role: string, p: PrivilegeDef): boolean {
    return p.defaults.includes(role) !== this.granted(role, p.code);
  }

  toggle(role: string, code: string): void {
    const d = { ...this.draft() };
    const s = new Set(d[role]);
    if (s.has(code)) s.delete(code); else s.add(code);
    d[role] = s;
    this.draft.set(d);
    this.message.set(null);
  }

  discard(): void {
    const m = this.matrix();
    if (m) this.apply(m);
  }

  save(): void {
    const grants: Record<string, string[]> = {};
    for (const [r, s] of Object.entries(this.draft())) grants[r] = [...s];
    this.busy.set(true);
    this.api.savePrivileges(grants).subscribe({
      next: (m) => {
        this.busy.set(false);
        this.apply(m);
        this.message.set({ ok: true, text: 'Privilèges enregistrés. Les utilisateurs connectés voient leurs menus mis à jour dans la minute.' });
        this.auth.syncMe();
      },
      error: (e) => {
        this.busy.set(false);
        this.message.set({ ok: false, text: e?.error?.message || 'Enregistrement impossible' });
      },
    });
  }

  resetDefaults(): void {
    if (!window.confirm('Rétablir les privilèges par défaut pour tous les profils ? Les réglages personnalisés seront perdus.')) return;
    this.busy.set(true);
    this.api.resetPrivileges().subscribe({
      next: (m) => {
        this.busy.set(false);
        this.apply(m);
        this.message.set({ ok: true, text: 'Privilèges par défaut rétablis.' });
        this.auth.syncMe();
      },
      error: (e) => {
        this.busy.set(false);
        this.message.set({ ok: false, text: e?.error?.message || 'Action impossible' });
      },
    });
  }

  countGranted(role: string, kind: 'PAGE' | 'ACTION'): number {
    const s = this.draft()[role];
    return (this.matrix()?.privileges ?? []).filter((p) => p.kind === kind && (s?.has(p.code) || p.locked.includes(role))).length;
  }

  countUsers(role: string): number {
    return this.users().filter((u) => u.role === role).length;
  }

  label(r: string): string {
    return ROLE_LABELS[r] ?? r;
  }

  desc(r: string): string {
    return ROLE_DESCRIPTIONS[r] ?? '';
  }
}
