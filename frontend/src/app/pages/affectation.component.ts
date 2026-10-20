import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { ZoneStore } from '../core/zone.store';
import { ParkOverview, Passport, VehicleSummary } from '../core/models';
import { ZoneGridComponent } from '../shared/zone-grid.component';

/**
 * Affectation des véhicules depuis le poste de supervision :
 * réception d'un nouveau véhicule, choix d'une place libre sur le plan, réaffectation.
 */
@Component({
  selector: 'dat-affectation',
  standalone: true,
  imports: [FormsModule, RouterLink, ZoneGridComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><h1>Affectation des véhicules</h1>
          <div class="sub">Choisir une place libre sur le plan. Sans vérification sur place, un agent confirme en scannant le QR de la place.</div></div>
        <span class="pill">Données fictives de démonstration</span>
      </div>

      <div class="layout">
        <aside class="col" style="gap: 16px">
          <form class="card col" (ngSubmit)="find()">
            <h2>Rechercher un véhicule</h2>
            <div class="row">
              <label class="sr-only" for="fq">VIN, code DAT ou 6 derniers caractères</label>
              <input id="fq" name="fq" class="field grow" [(ngModel)]="query" placeholder="VIN, DAT-218456, 218456…">
              <button type="submit" class="btn">Ouvrir</button>
            </div>
          </form>

          <section class="card col">
            <h2>À affecter <span class="count">{{ toAssign().length }}</span></h2>
            @for (v of toAssign(); track v.vin) {
              <button type="button" class="item" [class.active]="v.vin === selected()?.vehicle?.vin" (click)="open(v.vin)">
                <strong class="mono">{{ v.shortCode }}</strong><span class="muted">{{ v.model }} · {{ v.color }}</span>
              </button>
            } @empty { <span class="muted small">Aucun véhicule sans place.</span> }
          </section>

          <section class="card col">
            <h2>Position à confirmer <span class="count">{{ toConfirm().length }}</span></h2>
            @for (v of toConfirm().slice(0, 8); track v.vin) {
              <button type="button" class="item" [class.active]="v.vin === selected()?.vehicle?.vin" (click)="open(v.vin)">
                <strong class="mono">{{ v.shortCode }}</strong><span class="muted">{{ v.placeLabel }}</span>
              </button>
            } @empty { <span class="muted small">Toutes les positions sont confirmées.</span> }
          </section>

          <details class="card" [open]="showReception()">
            <summary (click)="$event.preventDefault(); showReception.set(!showReception())"><strong>Nouvelle réception</strong></summary>
            <form class="col" style="margin-top: 12px" (ngSubmit)="receive()">
              <label class="col">VIN (17 caractères)
                <input class="field mono" name="vin" [(ngModel)]="rec.vin" maxlength="17" (blur)="checkVin()" required>
              </label>
              @for (m of vinMessages(); track m) { <span class="warn small">{{ m }}</span> }
              <div class="grid2">
                <label class="col">Modèle<input class="field" name="model" [(ngModel)]="rec.model"></label>
                <label class="col">Couleur<input class="field" name="color" [(ngModel)]="rec.color"></label>
                <label class="col">N° de BL<input class="field" name="bl" [(ngModel)]="rec.blNumber"></label>
                <label class="col">Escale<input class="field" name="escale" [(ngModel)]="rec.escale"></label>
                <label class="col">Bénéficiaire<input class="field" name="ben" [(ngModel)]="rec.beneficiaryName"></label>
                <label class="col">CNI (4 derniers)<input class="field" name="cni" [(ngModel)]="rec.beneficiaryIdSuffix" maxlength="6"></label>
              </div>
              <label class="col">Transitaire<input class="field" name="fw" [(ngModel)]="rec.forwarder"></label>
              <button type="submit" class="btn dark" [disabled]="busy() || rec.vin.trim().length !== 17">Créer le passeport</button>
            </form>
          </details>
        </aside>

        <div class="col" style="gap: 16px">
          @if (selected(); as p) {
            <section class="card vcard">
              <div class="col" style="gap: 2px">
                <span class="kicker">Véhicule sélectionné</span>
                <strong class="mono big">{{ p.vehicle.vin }}</strong>
                <span class="muted">{{ p.vehicle.model }} · {{ p.vehicle.color }} · BL {{ p.vehicle.blNumber || '—' }}</span>
              </div>
              <span class="grow"></span>
              <div class="col right">
                <span class="muted small">Place actuelle</span>
                <strong class="place">{{ p.vehicle.placeLabel }}</strong>
                <span [class]="'badge ' + p.vehicle.status">{{ p.vehicle.statusLabel }}</span>
              </div>
            </section>

            <section class="card col">
              <div class="row wrap">
                <h2>Choisir une place libre</h2>
                <span class="grow"></span>
                @for (z of zones; track z) {
                  <button type="button" class="chip" [attr.aria-pressed]="zone() === z" (click)="zone.set(z)">Zone {{ z }}</button>
                }
                <button type="button" class="btn sm" (click)="suggest()">Suggérer une place</button>
              </div>
              @if (currentZone(); as z) {
                <dat-zone-grid [zone]="z" [selectable]="true" [selectedVin]="p.vehicle.vin"
                               [selectedPlace]="target()" (placePick)="target.set($event)" />
                <span class="muted small">Cases en pointillés verts : places libres (cliquer pour choisir). {{ freeCount(z) }} places libres en zone {{ z.zone }}.</span>
              }
            </section>

            <section class="card col confirm">
              <div class="row">
                <div class="col grow" style="gap: 2px">
                  <span class="muted small">Nouvelle place</span>
                  <strong class="place">{{ target() ? target()!.replaceAll('-', ' · ') : 'Aucune place choisie' }}</strong>
                </div>
                <label class="row check">
                  <input type="checkbox" [(ngModel)]="verified" name="verified">
                  Position vérifiée sur place
                </label>
              </div>
              <span class="muted small">
                {{ verified ? 'La position sera confirmée immédiatement (trace « vérification manuelle »).'
                            : 'La place est réservée ; le véhicule passe « Position à confirmer » et une tâche est envoyée à l\\'agent de la zone.' }}
              </span>
              <div class="row">
                <a class="btn" [routerLink]="['/parc']" [queryParams]="{ vin: p.vehicle.vin }">Voir le passeport</a>
                <span class="grow"></span>
                <button type="button" class="btn primary" [disabled]="!target() || busy()" (click)="assign(p)">
                  {{ p.vehicle.placeCode ? 'Réaffecter le véhicule' : 'Affecter le véhicule' }}
                </button>
              </div>
            </section>
          } @else {
            <section class="card empty">Sélectionnez un véhicule à gauche, recherchez-le, ou créez une nouvelle réception.</section>
          }
          @if (message(); as m) { <div class="banner" [class.ok]="m.ok" [class.danger]="!m.ok" role="status">{{ m.text }}</div> }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: 360px minmax(0, 1fr); gap: 16px; align-items: start; }
    h2 { font-size: 15px; font-weight: 600; }
    .count { font-size: 12px; background: var(--ground-2); border-radius: 999px; padding: 2px 8px; margin-left: 6px; }
    .item { display: flex; justify-content: space-between; align-items: center; gap: 8px; width: 100%; text-align: left; background: #fff; border: 1px solid var(--line); border-radius: 8px; padding: 8px 10px; cursor: pointer; }
    .item:hover, .item.active { border-color: var(--orange); background: #fff6ee; }
    .small { font-size: 12px; } .warn { color: var(--warn-ink); }
    .grid2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
    label.col { font-size: 12px; font-weight: 600; gap: 4px; }
    .vcard { display: flex; align-items: center; gap: 16px; }
    .kicker { font-size: 12px; font-weight: 600; letter-spacing: .8px; text-transform: uppercase; color: var(--rust); }
    .big { font-size: 20px; }
    .right { align-items: flex-end; gap: 4px; }
    .place { font-family: var(--display); font-size: 22px; }
    .wrap { flex-wrap: wrap; }
    .confirm { border: 1.5px solid var(--ink); }
    .check { font-weight: 600; gap: 8px; cursor: pointer; }
    .check input { width: 18px; height: 18px; accent-color: var(--rust); }
    .empty { color: var(--muted); padding: 40px; text-align: center; }
    summary { cursor: pointer; }
    @media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } }
  `],
})
export class AffectationComponent implements OnInit {
  private api = inject(ApiService);
  zoneStore = inject(ZoneStore);
  /** ?vin=… pour ouvrir directement un véhicule. */
  vin = input<string | undefined>();

  get zones(): string[] { return this.zoneStore.codes(); }
  vehicles = signal<VehicleSummary[]>([]);
  overview = signal<ParkOverview | null>(null);
  selected = signal<Passport | null>(null);
  zone = signal('B');
  target = signal<string | null>(null);
  busy = signal(false);
  message = signal<{ ok: boolean; text: string } | null>(null);
  showReception = signal(false);
  vinMessages = signal<string[]>([]);
  verified = false;
  query = '';
  rec = { vin: '', model: '', color: '', blNumber: '', escale: '2026-114', beneficiaryName: '', beneficiaryIdSuffix: '', forwarder: '' };

  toAssign = computed(() => this.vehicles().filter((v) => !v.placeCode && v.status !== 'EXITED'));
  toConfirm = computed(() => this.vehicles().filter((v) => v.status === 'POSITION_TO_CONFIRM' && !!v.placeCode));
  currentZone = computed(() => this.overview()?.zones.find((z) => z.zone === this.zone()) ?? null);

  ngOnInit(): void {
    this.zoneStore.load();
    this.reload();
    const v = this.vin();
    if (v) this.open(v);
  }

  reload(): void {
    this.api.vehicles({}).subscribe((v) => this.vehicles.set(v));
    this.api.park().subscribe((o) => this.overview.set(o));
  }

  open(vin: string): void {
    this.message.set(null);
    this.api.passport(vin).subscribe({
      next: (p) => {
        this.selected.set(p);
        this.target.set(null);
        this.zone.set(p.vehicle.zone ?? 'B');
      },
      error: (e) => this.fail(e),
    });
  }

  find(): void {
    if (this.query.trim()) this.open(this.query.trim().toUpperCase());
  }

  suggest(): void {
    this.api.suggestPlace(this.zone()).subscribe({
      next: (s) => this.target.set(s.placeCode),
      error: (e) => this.fail(e),
    });
  }

  freeCount(z: { allees: { vin: string | null }[][] }): number {
    return z.allees.reduce((n, a) => n + a.filter((p) => !p.vin).length, 0);
  }

  assign(p: Passport): void {
    const place = this.target();
    if (!place) return;
    this.busy.set(true);
    this.api.assign(p.vehicle.vin, place, this.verified).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.selected.set(res);
        this.target.set(null);
        this.message.set({
          ok: true,
          text: this.verified
            ? `${res.vehicle.shortCode} affecté en ${res.vehicle.placeLabel} · position confirmée`
            : `${res.vehicle.shortCode} affecté en ${res.vehicle.placeLabel} · tâche de scan envoyée à l'agent de la zone`,
        });
        this.reload();
      },
      error: (e) => this.fail(e),
    });
  }

  checkVin(): void {
    if (!this.rec.vin.trim()) return;
    this.api.vinCheck(this.rec.vin).subscribe((c) => {
      this.rec.vin = c.vin;
      this.vinMessages.set(c.validFormat ? c.messages.filter((m) => !m.startsWith('Clé de contrôle')) : ['VIN invalide : 17 caractères, sans I, O ni Q.', ...c.messages]);
    });
  }

  receive(): void {
    this.busy.set(true);
    const r = this.rec;
    this.api.receive({
      vin: r.vin.trim().toUpperCase(),
      model: r.model || undefined,
      color: r.color || undefined,
      blNumber: r.blNumber ? r.blNumber.toUpperCase() : undefined,
      escale: r.escale || undefined,
      beneficiaryName: r.beneficiaryName || undefined,
      beneficiaryIdSuffix: r.beneficiaryIdSuffix || undefined,
      forwarder: r.forwarder || undefined,
    }).subscribe({
      next: (p) => {
        this.busy.set(false);
        this.selected.set(p);
        this.target.set(null);
        this.showReception.set(false);
        this.vinMessages.set([]);
        this.rec = { vin: '', model: '', color: '', blNumber: '', escale: '2026-114', beneficiaryName: '', beneficiaryIdSuffix: '', forwarder: '' };
        this.message.set({ ok: true, text: `Passeport créé pour ${p.vehicle.shortCode} : choisissez maintenant sa place.` });
        this.suggest();
        this.reload();
      },
      error: (e) => this.fail(e),
    });
  }

  private fail(e: { status?: number; error?: { message?: string } }): void {
    this.busy.set(false);
    this.message.set({ ok: false, text: e?.status === 0 ? 'API injoignable' : (e?.error?.message ?? 'Action impossible') });
  }
}
