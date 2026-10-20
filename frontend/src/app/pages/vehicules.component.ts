import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../core/api.service';
import { ZoneStore } from '../core/zone.store';
import { VehicleSummary } from '../core/models';

/** Module Véhicules : inventaire filtrable du parc. */
@Component({
  selector: 'dat-vehicules',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><h1>Véhicules</h1><div class="sub">Inventaire du parc · chaque véhicule a un passeport numérique lié à son VIN</div></div>
        <span class="pill">Données fictives de démonstration</span>
      </div>

      <div class="row filters">
        <label class="sr-only" for="q">Rechercher</label>
        <input id="q" class="field" style="width: 260px" placeholder="VIN, code DAT, BL, place…" [(ngModel)]="q" (keyup.enter)="load()">
        @for (c of chips; track c.key) {
          <button type="button" class="chip" [attr.aria-pressed]="status() === c.key" (click)="setStatus(c.key)">
            {{ c.label }} <span class="count">{{ counts()[c.countKey] || '' }}</span>
          </button>
        }
        <span class="grow"></span>
        <label class="row muted">Zone
          <select class="field" [(ngModel)]="zone" (change)="load()">
            <option value="">Toutes</option>
            @for (z of zoneStore.codes(); track z) { <option [value]="z">{{ z }}{{ z === 'B' ? ' (pilote)' : '' }}</option> }
          </select>
        </label>
      </div>

      <div class="card" style="padding: 0; overflow: auto">
        <table class="data">
          <thead>
            <tr><th>VIN</th><th>Véhicule</th><th>Escale</th><th>Emplacement</th><th>Statut</th><th>Formalités</th><th>État</th><th>Sur parc</th><th>Dernier scan</th></tr>
          </thead>
          <tbody>
            @for (v of rows(); track v.vin) {
              <tr (click)="open(v)" class="clickable">
                <td class="mono strong">{{ v.vin }}</td>
                <td>{{ v.model }} · {{ v.color }}</td>
                <td class="mono">{{ v.escale }}</td>
                <td class="mono strong">{{ v.placeLabel }}</td>
                <td><span [class]="'badge ' + v.status">{{ v.statusLabel }}</span></td>
                <td>
                  <span class="bar"><span [style.width.%]="v.formalitiesTotal ? v.formalitiesDone * 100 / v.formalitiesTotal : 0"></span></span>
                  {{ v.formalitiesDone }}/{{ v.formalitiesTotal }}
                </td>
                <td [class.warn]="v.reservesCount > 0">{{ v.reservesCount ? v.reservesCount + ' réserve(s)' : 'RAS' }}</td>
                <td [class.late]="v.daysOnPark >= 5">{{ v.daysOnPark }} j</td>
                <td class="muted small">
                  @if (v.lastScan) { {{ v.lastScan.at | date: 'dd/MM HH:mm' }} · {{ v.lastScan.by }} }
                </td>
              </tr>
            } @empty {
              <tr><td colspan="9" class="muted">Aucun véhicule ne correspond aux filtres.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <div class="muted">{{ rows().length }} véhicule(s) affiché(s)</div>
    </div>
  `,
  styles: [`
    .filters { flex-wrap: wrap; }
    .count { font-weight: 500; opacity: .8; margin-left: 4px; }
    .strong { font-weight: 600; }
    .clickable { cursor: pointer; }
    .clickable:hover td { background: #fff6ee; }
    .bar { display: inline-flex; width: 40px; height: 6px; border-radius: 3px; background: var(--ground-2); overflow: hidden; margin-right: 6px; vertical-align: middle; }
    .bar span { background: var(--slot-occupied); }
    .warn { color: var(--warn-ink); font-weight: 600; }
    .late { color: var(--danger); font-weight: 600; }
    .small { font-size: 12px; }
  `],
})
export class VehiculesComponent implements OnInit {
  private api = inject(ApiService);
  zoneStore = inject(ZoneStore);
  private router = inject(Router);
  rows = signal<VehicleSummary[]>([]);
  counts = signal<Record<string, number>>({});
  status = signal<string>('');
  q = '';
  zone = '';

  chips = [
    { key: '', label: 'Tous', countKey: 'ALL' },
    { key: 'AWAITING_AUTHORIZATION', label: 'En attente', countKey: 'AWAITING_AUTHORIZATION' },
    { key: 'READY', label: 'Prêts à enlever', countKey: 'READY' },
    { key: 'POSITION_TO_CONFIRM', label: 'Position à confirmer', countKey: 'POSITION_TO_CONFIRM' },
    { key: 'EXITED', label: 'Sortis', countKey: 'EXITED' },
  ];

  ngOnInit(): void {
    this.zoneStore.load();
    this.api.counts().subscribe((c) => this.counts.set(c));
    this.load();
  }

  setStatus(s: string): void {
    this.status.set(s);
    this.load();
  }

  load(): void {
    this.api.vehicles({ status: this.status(), zone: this.zone, q: this.q }).subscribe((r) => this.rows.set(r));
  }

  open(v: VehicleSummary): void {
    this.router.navigate(['/parc'], { queryParams: { vin: v.vin } });
  }
}
