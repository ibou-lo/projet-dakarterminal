import { Component, computed, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { ZoneInfo } from '../core/models';
import { ZoneStore } from '../core/zone.store';

/** Gestion des zones du plan : ajout d'une zone, ajout d'allées, renommage, suppression. */
@Component({
  selector: 'dat-zones-dialog',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="backdrop" (click)="close.emit()" role="presentation">
      <section class="dlg" role="dialog" aria-modal="true" aria-labelledby="zd-title" (click)="$event.stopPropagation()">
        <header class="row">
          <div class="col0">
            <span class="kicker">Plan du parc</span>
            <h2 id="zd-title">Zones du parc</h2>
          </div>
          <span class="grow"></span>
          <button type="button" class="x" aria-label="Fermer" (click)="close.emit()">✕</button>
        </header>

        <div class="body">
          <!-- Nouvelle zone -->
          <form class="new" (ngSubmit)="create()" #f="ngForm">
            <h3>Ajouter une zone</h3>
            <div class="grid2">
              <label>Code
                <input class="field mono" name="code" [(ngModel)]="code" maxlength="3" required placeholder="E"
                       (ngModelChange)="code = $event.toUpperCase()">
              </label>
              <label>Usage
                <select class="field" name="usage" [(ngModel)]="usage">
                  @for (u of usages; track u) { <option [value]="u">{{ u }}</option> }
                </select>
              </label>
            </div>
            <label>Libellé
              <input class="field" name="label" [(ngModel)]="label" maxlength="60" required placeholder="Extension quai 3">
            </label>
            <div class="grid2">
              <label>Allées
                <input class="field" type="number" name="allees" [ngModel]="allees()" (ngModelChange)="allees.set(+$event)" min="1" max="20" required>
              </label>
              <label>Places par allée
                <input class="field" type="number" name="ppa" [ngModel]="ppa()" (ngModelChange)="ppa.set(+$event)" min="1" max="60" required>
              </label>
            </div>

            <div class="preview" aria-hidden="true">
              @for (a of previewAllees(); track $index) {
                <div class="allee">
                  <span class="an">{{ $index + 1 }}</span>
                  @for (p of previewPlaces(); track $index) { <i></i> }
                  @if (ppa() > previewPlaces().length) { <span class="more">+{{ ppa() - previewPlaces().length }}</span> }
                </div>
              }
              @if (allees() > previewAllees().length) { <span class="more">+ {{ allees() - previewAllees().length }} allée(s)</span> }
            </div>
            <p class="muted small">
              {{ total() }} places créées · codes {{ codeOrX() }}-1-1 à {{ codeOrX() }}-{{ allees() }}-{{ ppa() }} · QR « PL:… » prêts à imprimer.
              @if (duplicate()) { <strong class="err">Le code {{ code }} existe déjà.</strong> }
              @if (total() > 1200) { <strong class="err">Maximum 1 200 places par zone.</strong> }
            </p>
            <button class="btn primary" type="submit" [disabled]="!f.valid || duplicate() || total() > 1200 || total() < 1 || busy()">
              Créer la zone
            </button>
          </form>

          <!-- Zones existantes -->
          <div class="list">
            <h3>Zones existantes</h3>
            @for (z of store.zones(); track z.code) {
              <div class="zone">
                <div class="zc">{{ z.code }}</div>
                <div class="zi">
                  @if (editing() === z.code) {
                    <div class="row">
                      <input class="field grow" [(ngModel)]="editLabel" [attr.aria-label]="'Libellé de la zone ' + z.code" maxlength="60">
                      <button class="btn sm primary" type="button" (click)="rename(z)">OK</button>
                      <button class="btn sm" type="button" (click)="editing.set(null)">Annuler</button>
                    </div>
                  } @else {
                    <strong>{{ z.label }}</strong>
                  }
                  <span class="muted small">{{ z.allees }} allées · {{ z.places }} places · {{ z.occupied }} occupées@if (z.usage) { · {{ z.usage }} }</span>
                  @if (adding() === z.code) {
                    <div class="row add">
                      <label>Allées <input class="field n" type="number" min="1" max="10" [(ngModel)]="addCount"></label>
                      <label>× places <input class="field n" type="number" min="1" max="60" [(ngModel)]="addPpa"></label>
                      <button class="btn sm primary" type="button" (click)="addAllees(z)">Ajouter</button>
                      <button class="btn sm" type="button" (click)="adding.set(null)">Annuler</button>
                    </div>
                  }
                </div>
                <div class="za">
                  <button class="btn sm" type="button" (click)="startRename(z)">Renommer</button>
                  <button class="btn sm" type="button" (click)="startAdd(z)">+ Allée</button>
                  <button class="btn sm danger" type="button" [disabled]="!z.deletable"
                          [title]="z.deletable ? 'Supprimer la zone et ses places' : 'Zone utilisée par des véhicules : conservée pour la traçabilité'"
                          (click)="remove(z)">Supprimer</button>
                </div>
              </div>
            }
          </div>
        </div>

        @if (message(); as m) {
          <div class="msg" [class.ok]="m.ok" role="status">
            {{ m.text }}
            @if (m.ok && lastCreated()) { · <a routerLink="/etiquettes" (click)="close.emit()">Imprimer les QR des places</a> }
          </div>
        }
      </section>
    </div>
  `,
  styles: [`
    .backdrop { position: fixed; inset: 0; background: rgba(15, 29, 43, .55); display: flex; align-items: center; justify-content: center; z-index: 100; padding: 16px; }
    .dlg { width: 980px; max-width: 100%; max-height: 100%; overflow: auto; background: #fff; border-radius: 18px; padding: 22px; display: flex; flex-direction: column; gap: 14px; box-shadow: 0 20px 60px rgba(0,0,0,.3); }
    .col0 { display: flex; flex-direction: column; }
    .kicker { font-size: 12px; font-weight: 600; letter-spacing: .8px; text-transform: uppercase; color: var(--muted); }
    h2 { font-family: var(--display); font-size: 24px; font-weight: 800; }
    h3 { font-size: 15px; font-weight: 700; }
    .x { border: 0; background: transparent; font-size: 18px; cursor: pointer; width: 36px; height: 36px; border-radius: 8px; }
    .x:hover { background: var(--ground); }
    .body { display: grid; grid-template-columns: 380px minmax(0, 1fr); gap: 20px; align-items: start; }
    .new { display: flex; flex-direction: column; gap: 10px; background: var(--ground); border-radius: 12px; padding: 16px; }
    label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; color: var(--text-2); }
    .grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .field { width: 100%; }
    .mono { font-family: var(--mono); text-transform: uppercase; }
    .preview { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 10px; display: flex; flex-direction: column; gap: 4px; min-height: 40px; }
    .allee { display: flex; align-items: center; gap: 3px; }
    .an { width: 18px; font-size: 11px; color: var(--muted); }
    .allee i { width: 9px; height: 13px; border: 1px dashed #a49d8e; border-radius: 2px; }
    .more { font-size: 11px; color: var(--muted); margin-left: 4px; }
    .small { font-size: 12px; margin: 0; }
    .err { color: var(--danger, #A3261B); display: block; }
    .list { display: flex; flex-direction: column; gap: 8px; }
    .zone { display: grid; grid-template-columns: 44px minmax(0, 1fr) auto; gap: 12px; align-items: center; border: 1px solid var(--line); border-radius: 12px; padding: 10px 12px; }
    .zc { width: 40px; height: 40px; border-radius: 10px; background: var(--ink); color: #fff; display: flex; align-items: center; justify-content: center; font-family: var(--display); font-weight: 800; font-size: 17px; }
    .zi { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .za { display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end; }
    .add { margin-top: 6px; flex-wrap: wrap; }
    .add label { flex-direction: row; align-items: center; gap: 6px; }
    .n { width: 70px; height: 34px; }
    .danger { color: var(--danger, #A3261B); }
    .danger:disabled { opacity: .45; cursor: not-allowed; }
    .msg { border-radius: 10px; padding: 10px 14px; background: var(--danger-bg, #FCF1EF); color: var(--danger, #A3261B); font-size: 14px; }
    .msg.ok { background: #DDF0E5; color: #145C3C; }
    @media (max-width: 860px) { .body { grid-template-columns: 1fr; } .zone { grid-template-columns: 44px 1fr; } .za { grid-column: 1 / -1; justify-content: flex-start; } }
  `],
})
export class ZonesDialogComponent {
  private api = inject(ApiService);
  store = inject(ZoneStore);
  close = output<void>();
  /** Émis après toute modification : le plan doit être rechargé. */
  changed = output<void>();

  readonly usages = ['Véhicules légers', 'Utilitaires', 'Engins & poids lourds', 'Véhicules prêts', 'Véhicules électriques', 'Tampon / débord'];
  code = '';
  label = '';
  usage = this.usages[0];
  allees = signal(4);
  ppa = signal(24);
  total = computed(() => Math.max(0, this.allees() || 0) * Math.max(0, this.ppa() || 0));
  previewAllees = computed(() => Array.from({ length: Math.min(Math.max(this.allees() || 0, 0), 6) }));
  previewPlaces = computed(() => Array.from({ length: Math.min(Math.max(this.ppa() || 0, 0), 30) }));

  busy = signal(false);
  message = signal<{ ok: boolean; text: string } | null>(null);
  lastCreated = signal<string | null>(null);
  editing = signal<string | null>(null);
  adding = signal<string | null>(null);
  editLabel = '';
  addCount = 1;
  addPpa = 24;

  constructor() {
    this.store.load(true);
  }

  codeOrX(): string {
    return this.code.trim() || 'X';
  }

  duplicate(): boolean {
    const c = this.code.trim().toUpperCase();
    return !!c && this.store.zones().some((z) => z.code === c);
  }

  create(): void {
    this.busy.set(true);
    this.api.createZone({ code: this.code.trim(), label: this.label.trim(), usage: this.usage, allees: this.allees(), placesParAllee: this.ppa() })
      .subscribe({
        next: (z) => {
          this.done(`Zone ${z.code} créée : ${z.allees} allées, ${z.places} places.`);
          this.lastCreated.set(z.code);
          this.code = '';
          this.label = '';
        },
        error: (e) => this.fail(e),
      });
  }

  startRename(z: ZoneInfo): void {
    this.adding.set(null);
    this.editLabel = z.label;
    this.editing.set(z.code);
  }

  rename(z: ZoneInfo): void {
    if (!this.editLabel.trim()) return;
    this.busy.set(true);
    this.api.updateZone(z.code, this.editLabel.trim(), z.usage).subscribe({
      next: () => { this.editing.set(null); this.done(`Zone ${z.code} renommée.`); },
      error: (e) => this.fail(e),
    });
  }

  startAdd(z: ZoneInfo): void {
    this.editing.set(null);
    this.addCount = 1;
    this.addPpa = z.allees ? Math.round(z.places / z.allees) : 24;
    this.adding.set(z.code);
  }

  addAllees(z: ZoneInfo): void {
    this.busy.set(true);
    this.api.addAllees(z.code, +this.addCount, +this.addPpa).subscribe({
      next: (r) => { this.adding.set(null); this.done(`Zone ${r.code} : ${r.allees} allées, ${r.places} places.`); },
      error: (e) => this.fail(e),
    });
  }

  remove(z: ZoneInfo): void {
    if (!z.deletable || !window.confirm(`Supprimer la zone ${z.code} et ses ${z.places} places ?`)) return;
    this.busy.set(true);
    this.api.deleteZone(z.code).subscribe({
      next: () => this.done(`Zone ${z.code} supprimée.`),
      error: (e) => this.fail(e),
    });
  }

  private done(text: string): void {
    this.busy.set(false);
    this.lastCreated.set(null);
    this.message.set({ ok: true, text });
    this.store.load(true);
    this.changed.emit();
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private fail(e: any): void {
    this.busy.set(false);
    const v = e?.error?.fields ? Object.values(e.error.fields).join(' · ') : null;
    this.message.set({ ok: false, text: e?.status === 0 ? 'API injoignable' : (v || e?.error?.message || 'Action impossible') });
  }
}
