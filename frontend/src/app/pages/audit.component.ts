import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuditEntry } from '../core/models';
import { ROLE_LABELS } from '../core/permissions';

/** Journal d'audit : connexions, refus d'accès, gestion des comptes et actions métier. */
@Component({
  selector: 'dat-audit',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow">
          <h1>Journal d'audit</h1>
          <div class="sub">Qui a fait quoi, quand et depuis où — écrit par le serveur, non modifiable</div>
        </div>
        <button type="button" class="btn" (click)="exportCsv()" [disabled]="!entries().length">Exporter (CSV)</button>
      </div>

      <div class="tiles">
        <div class="tile"><span class="label">Événements affichés</span><span class="value">{{ entries().length }}</span></div>
        <div class="tile"><span class="label">Connexions</span><span class="value">{{ count('CONNEXION') }}</span></div>
        <div class="tile"><span class="label">Échecs & verrouillages</span><span class="value warn">{{ count('ECHEC_CONNEXION') + count('COMPTE_VERROUILLE') + count('CONNEXION_REFUSEE') }}</span></div>
        <div class="tile"><span class="label">Accès refusés</span><span class="value warn">{{ count('ACCES_REFUSE') }}</span></div>
      </div>

      <section class="card col">
        <div class="row filters">
          <div class="chips" role="group" aria-label="Catégorie">
            @for (c of categories; track c.key) {
              <button type="button" class="chip" [class.on]="category() === c.key" (click)="category.set(c.key); load()">{{ c.label }}</button>
            }
          </div>
          <span class="grow"></span>
          <select class="field" aria-label="Période" [ngModel]="days()" (ngModelChange)="days.set(+$event); load()">
            <option [ngValue]="1">24 heures</option>
            <option [ngValue]="7">7 jours</option>
            <option [ngValue]="30">30 jours</option>
            <option [ngValue]="90">90 jours</option>
          </select>
        </div>
        <div class="row filters">
          <select class="field" aria-label="Action" [ngModel]="action()" (ngModelChange)="action.set($event); load()">
            <option value="">Toutes les actions</option>
            @for (a of actions; track a.code) { <option [value]="a.code">{{ a.label }}</option> }
          </select>
          <input class="field" placeholder="Utilisateur (identifiant ou nom)" aria-label="Utilisateur"
                 [ngModel]="actorFilter()" (ngModelChange)="actorFilter.set($event)" (keyup.enter)="load()">
          <input class="field grow" placeholder="Rechercher : VIN, zone, ticket, détail…" aria-label="Recherche"
                 [ngModel]="q()" (ngModelChange)="q.set($event)" (keyup.enter)="load()">
          <button type="button" class="btn" (click)="load()">Filtrer</button>
          @if (actorFilter() || q() || action()) { <button type="button" class="btn ghost" (click)="clear()">Effacer</button> }
        </div>

        <div class="scroll">
          <table class="data">
            <thead><tr><th>Date</th><th>Événement</th><th>Utilisateur</th><th>Objet</th><th>Détails</th><th>Adresse IP</th></tr></thead>
            <tbody>
              @for (e of entries(); track e.id) {
                <tr [class.fail]="!e.success">
                  <td class="nowrap mono">{{ e.at | date: 'dd/MM/yyyy HH:mm:ss' }}</td>
                  <td>
                    <div class="ev">
                      <span class="dot" [attr.data-cat]="e.category" [class.ko]="!e.success"></span>
                      <div><strong>{{ e.label }}</strong><div class="muted small">{{ catLabel(e.category) }} · {{ e.action }}</div></div>
                    </div>
                  </td>
                  <td>
                    <button type="button" class="linkish" (click)="filterActor(e.actor)" [title]="'Voir l\\'activité de ' + e.actor">
                      {{ e.actorName || e.actor }}</button>
                    <div class="muted small">{{ e.actor }}@if (e.actorRole) { · {{ role(e.actorRole) }} }</div>
                  </td>
                  <td class="mono small">{{ e.target || '—' }}</td>
                  <td class="small">{{ e.details || '' }}</td>
                  <td class="mono small muted">{{ e.ip || '—' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="muted empty">{{ loading() ? 'Chargement…' : 'Aucun événement pour ces filtres.' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
        @if (entries().length >= limit) { <p class="muted small">Affichage limité aux {{ limit }} événements les plus récents : affinez les filtres.</p> }
      </section>
    </div>
  `,
  styles: [`
    .tile .value.warn { color: var(--warn); }
    .filters { flex-wrap: wrap; }
    .chips { display: flex; gap: 6px; flex-wrap: wrap; }
    .chip { border: 1.5px solid var(--line-2); background: #fff; border-radius: 999px; padding: 6px 14px; font: inherit; font-size: 13px; cursor: pointer; }
    .chip.on { background: var(--ink); border-color: var(--ink); color: #fff; }
    .scroll { overflow-x: auto; }
    .nowrap { white-space: nowrap; }
    .mono { font-family: var(--mono); font-size: 12px; }
    .muted { color: var(--muted); }
    .small { font-size: 12px; }
    .ev { display: flex; gap: 10px; align-items: flex-start; }
    .dot { width: 10px; height: 10px; border-radius: 50%; margin-top: 5px; flex: none; background: var(--muted); }
    .dot[data-cat='SECURITE'] { background: var(--info-ink); }
    .dot[data-cat='ADMINISTRATION'] { background: var(--rust); }
    .dot[data-cat='OPERATION'] { background: var(--ok); }
    .dot.ko { background: var(--danger) !important; box-shadow: 0 0 0 3px #f8deda; }
    tr.fail td { background: #fffaf8; }
    .linkish { border: 0; background: none; padding: 0; font: inherit; font-weight: 600; color: var(--ink); cursor: pointer; text-align: left; }
    .linkish:hover { color: var(--rust); text-decoration: underline; }
    .ghost { border-color: transparent; }
    .empty { text-align: center; padding: 28px; }
  `],
})
export class AuditComponent implements OnInit {
  private api = inject(ApiService);
  /** Paramètre d'URL ?actor=… (lien « Activité » de la page Utilisateurs). */
  actor = input<string | undefined>();
  /** Paramètre d'URL ?action=… (lien depuis Profils & privilèges). */
  actionParam = input<string | undefined>(undefined, { alias: 'action' });

  readonly limit = 1000;
  readonly categories = [
    { key: '', label: 'Tout' },
    { key: 'SECURITE', label: 'Sécurité' },
    { key: 'ADMINISTRATION', label: 'Administration' },
    { key: 'OPERATION', label: 'Opérations' },
  ];
  readonly actions = [
    { code: 'CONNEXION', label: 'Connexion' },
    { code: 'ECHEC_CONNEXION', label: 'Échec de connexion' },
    { code: 'COMPTE_VERROUILLE', label: 'Compte verrouillé' },
    { code: 'CONNEXION_REFUSEE', label: 'Connexion refusée' },
    { code: 'ACCES_REFUSE', label: 'Accès refusé' },
    { code: 'MOT_DE_PASSE_CHANGE', label: 'Mot de passe changé' },
    { code: 'UTILISATEUR_CREE', label: 'Compte créé' },
    { code: 'UTILISATEUR_MODIFIE', label: 'Compte modifié' },
    { code: 'PROFIL_MODIFIE', label: 'Profil modifié' },
    { code: 'COMPTE_DESACTIVE', label: 'Compte désactivé' },
    { code: 'COMPTE_REACTIVE', label: 'Compte réactivé' },
    { code: 'COMPTE_DEVERROUILLE', label: 'Compte déverrouillé' },
    { code: 'MOT_DE_PASSE_REINITIALISE', label: 'Mot de passe réinitialisé' },
    { code: 'PRIVILEGES_MODIFIES', label: 'Privilèges modifiés' },
    { code: 'PRIVILEGES_REINITIALISES', label: 'Privilèges remis par défaut' },
    { code: 'RECEPTION', label: 'Réception d\'un véhicule' },
    { code: 'STATIONNEMENT', label: 'Stationnement' },
    { code: 'AFFECTATION', label: 'Affectation' },
    { code: 'DEPLACEMENT', label: 'Déplacement' },
    { code: 'FORMALITE_RECUE', label: 'Formalité reçue' },
    { code: 'TICKET_EMIS', label: 'Ticket émis' },
    { code: 'SCAN_PORTAIL', label: 'Scan au portail' },
    { code: 'DECISION_SORTIE', label: 'Décision de sortie' },
    { code: 'MOUVEMENT_CLE', label: 'Mouvement de clé' },
    { code: 'ETIQUETTE_ASSOCIEE', label: 'Étiquette associée' },
    { code: 'ZONE_CREEE', label: 'Zone créée' },
  ];

  entries = signal<AuditEntry[]>([]);
  loading = signal(false);
  category = signal('');
  action = signal('');
  actorFilter = signal('');
  q = signal('');
  days = signal(7);

  private counts = computed(() => {
    const m = new Map<string, number>();
    for (const e of this.entries()) m.set(e.action, (m.get(e.action) ?? 0) + 1);
    return m;
  });

  ngOnInit(): void {
    const a = this.actor();
    if (a) { this.actorFilter.set(a); this.days.set(30); }
    const act = this.actionParam();
    if (act) { this.action.set(act); this.days.set(90); }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.audit({ category: this.category(), action: this.action(), actor: this.actorFilter(), q: this.q(), days: this.days(), limit: this.limit })
      .subscribe({ next: (e) => { this.entries.set(e); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  clear(): void {
    this.action.set('');
    this.actorFilter.set('');
    this.q.set('');
    this.load();
  }

  filterActor(actor: string): void {
    this.actorFilter.set(actor);
    this.load();
  }

  count(action: string): number {
    return this.counts().get(action) ?? 0;
  }

  catLabel(c: string): string {
    return ({ SECURITE: 'Sécurité', ADMINISTRATION: 'Administration', OPERATION: 'Opération' } as Record<string, string>)[c] ?? c;
  }

  role(r: string): string {
    return ROLE_LABELS[r] ?? r;
  }

  /** Export CSV (séparateur « ; » pour Excel en français, BOM UTF-8). */
  exportCsv(): void {
    const esc = (v: string | null | undefined) => `"${(v ?? '').replace(/"/g, '""')}"`;
    const head = ['Date', 'Catégorie', 'Action', 'Libellé', 'Identifiant', 'Nom', 'Profil', 'Objet', 'Détails', 'IP', 'Résultat'];
    const lines = this.entries().map((e) => [
      new Date(e.at).toLocaleString('fr-FR'), this.catLabel(e.category), e.action, e.label, e.actor, e.actorName,
      e.actorRole ? this.role(e.actorRole) : '', e.target, e.details, e.ip, e.success ? 'Succès' : 'Échec',
    ].map(esc).join(';'));
    const blob = new Blob(['﻿' + [head.join(';'), ...lines].join('\r\n')], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `audit-dakar-autotrack-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(a.href);
  }
}
