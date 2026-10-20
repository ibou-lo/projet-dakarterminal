import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { Incoherence, ParkOverview } from '../core/models';
import { ZoneGridComponent } from '../shared/zone-grid.component';

/** Carte « fraîcheur » : ancienneté de la dernière lecture de chaque place. */
@Component({
  selector: 'dat-fraicheur',
  standalone: true,
  imports: [ZoneGridComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><h1>Fraîcheur de l'inventaire</h1>
          <div class="sub">Chaque place prend la couleur de sa dernière lecture : QR, portique RFID ou inventaire en rafale</div></div>
        <span class="pill">Données fictives de démonstration</span>
      </div>
      <div class="row wrap">
        <span class="muted">Source :</span>
        @for (s of sources; track s.key) {
          <button type="button" class="chip" [attr.aria-pressed]="source() === s.key" (click)="setSource(s.key)">{{ s.label }}</button>
        }
        <span class="grow"></span>
        <span class="legend">
          <i style="background: var(--fresh-1)"></i>&lt; 1 h <i style="background: var(--fresh-2)"></i>1–6 h
          <i style="background: var(--fresh-3)"></i>6–24 h <i style="background: var(--fresh-4); border: 1px solid #b7c4d4"></i>&gt; 24 h
          <i style="border: 2.5px solid #c0392b"></i>Incohérence
        </span>
      </div>
      <div class="layout">
        <div class="zones">
          @for (z of overview()?.zones ?? []; track z.zone) {
            <dat-zone-grid [zone]="z" mode="freshness" />
          }
        </div>
        <aside class="col" style="gap: 16px">
          <section class="card col">
            <h2>Incohérences détectées</h2>
            @for (i of incoherences(); track $index) {
              <div class="inc"><strong class="mono">{{ i.kind === 'VIDE' ? 'Place ' + i.placeCode : i.vin }}</strong><span>{{ i.message }}</span></div>
            } @empty { <span class="muted">Aucune incohérence sur 24 h.</span> }
            @if (incoherences().length) {
              @if (taskSent()) { <span class="ok" role="status">Tâche(s) de vérification envoyée(s)</span> }
              @else { <button type="button" class="btn primary" (click)="createTasks()">Créer une tâche de vérification</button> }
            }
          </section>
          <section class="card col">
            <h2>Allées à relire en priorité</h2>
            @for (a of stale(); track a.code) {
              <div class="row stale"><span class="mono strong">{{ a.code }}</span><span class="grow muted">{{ a.count }} véhicules</span><span class="age">{{ a.age }}</span></div>
            }
            <span class="muted small">L'assistant IA peut proposer un itinéraire d'inventaire qui couvre ces allées en premier.</span>
          </section>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    .wrap { flex-wrap: wrap; }
    .legend { display: flex; gap: 6px; align-items: center; font-size: 12px; color: var(--text-2); }
    .legend i { display: inline-block; width: 14px; height: 18px; border-radius: 3px; margin-left: 8px; box-sizing: border-box; }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 16px; align-items: start; }
    .zones { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
    .inc { border: 1px solid #ebc9c4; background: var(--danger-bg); border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
    .stale { border-top: 1px solid var(--ground-2); padding-top: 8px; }
    .age { font-size: 12px; font-weight: 600; color: var(--warn-ink); background: var(--warn-bg); border-radius: 999px; padding: 2px 8px; }
    .strong { font-weight: 600; } .small { font-size: 12px; } .ok { color: var(--ok-ink); font-weight: 600; }
    @media (max-width: 1200px) { .layout { grid-template-columns: 1fr; } .zones { grid-template-columns: 1fr; } }
  `],
})
export class FraicheurComponent implements OnInit {
  private api = inject(ApiService);
  overview = signal<ParkOverview | null>(null);
  incoherences = signal<Incoherence[]>([]);
  source = signal('ALL');
  taskSent = signal(false);

  sources = [
    { key: 'ALL', label: 'Toutes les lectures' },
    { key: 'QR', label: 'Scans QR' },
    { key: 'RFID_PORTIQUE', label: 'Portiques RFID' },
    { key: 'RAFALE', label: 'Inventaire en rafale' },
  ];

  /** Allées dont la lecture la plus récente est la plus ancienne. */
  stale = computed(() => {
    const out: { code: string; count: number; minutes: number; age: string }[] = [];
    for (const z of this.overview()?.zones ?? []) {
      for (const allee of z.allees) {
        const occupied = allee.filter((p) => p.vin);
        if (!occupied.length) continue;
        const newest = Math.max(...occupied.map((p) => (p.lastReadAt ? new Date(p.lastReadAt).getTime() : 0)));
        const minutes = (Date.now() - newest) / 60000;
        out.push({ code: `${z.zone}·${allee[0].allee}`, count: occupied.length, minutes, age: minutes > 1440 ? '> 24 h' : `${Math.round(minutes / 60)} h` });
      }
    }
    return out.sort((a, b) => b.minutes - a.minutes).slice(0, 4);
  });

  ngOnInit(): void {
    this.load();
    this.api.incoherences().subscribe((i) => this.incoherences.set(i));
  }

  setSource(s: string): void {
    this.source.set(s);
    this.load();
  }

  load(): void {
    this.api.park(this.source()).subscribe((o) => this.overview.set(o));
  }

  createTasks(): void {
    for (const i of this.incoherences()) {
      this.api.createTask(i.vin, i.placeCode, i.message).subscribe(() => this.taskSent.set(true));
    }
  }
}
