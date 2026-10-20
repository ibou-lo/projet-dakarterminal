import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { AgentActivity, MovementDto, MovementStats } from '../core/models';

const TYPE_LABELS: Record<string, string> = {
  DEBARQUEMENT: 'Débarquement', STATIONNEMENT: 'Stationnement', DEPLACEMENT: 'Déplacement', PRE_SORTIE: 'Pré-sortie', SORTIE: 'Sortie',
};

/** Module Mouvements : journal, activité horaire et régularisation des scans manquants. */
@Component({
  selector: 'dat-mouvements',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><h1>Mouvements</h1><div class="sub">Chaque changement de place est enregistré par scan : heure, agent, départ et arrivée</div></div>
        <span class="pill">Données fictives de démonstration</span>
      </div>

      @if (stats(); as s) {
        <div class="tiles">
          <div class="tile"><span class="label">Mouvements aujourd'hui</span><span class="value">{{ s.today }}</span></div>
          <div class="tile"><span class="label">Trace complète (départ + arrivée scannés)</span><span class="value ok">{{ s.traceCompletePct | number: '1.1-1' }} %</span></div>
          <div class="tile"><span class="label">Scans d'arrivée manquants</span><span class="value danger">{{ s.missingArrivals }}</span></div>
          <div class="tile"><span class="label">Durée moyenne d'un déplacement</span><span class="value">{{ s.avgMoveMinutes !== null ? (s.avgMoveMinutes | number: '1.1-1') + ' min' : '—' }}</span></div>
        </div>
      }

      <div class="layout">
        <div class="col" style="gap: 16px">
          <section class="card col">
            <h2>Mouvements par heure · aujourd'hui</h2>
            <svg [attr.viewBox]="'0 0 ' + chartW + ' ' + (chartH + 24)" class="chart" role="img" [attr.aria-label]="chartLabel()">
              @for (g of gridLines(); track g.v) {
                <line x1="0" [attr.x2]="chartW - 30" [attr.y1]="g.y" [attr.y2]="g.y" stroke="#EEEBE3" />
                <text [attr.x]="chartW - 24" [attr.y]="g.y + 4" class="axis">{{ g.v }}</text>
              }
              @for (b of bars(); track b.label) {
                <g>
                  <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" rx="3" fill="#3A4E64"><title>{{ b.label }} : {{ b.v }} mouvements</title></rect>
                  <text [attr.x]="b.x + b.w / 2" [attr.y]="chartH + 16" text-anchor="middle" class="axis">{{ b.label }}</text>
                  @if (b.peak) { <text [attr.x]="b.x + b.w / 2" [attr.y]="b.y - 6" text-anchor="middle" class="peak">{{ b.v }}</text> }
                </g>
              }
            </svg>
          </section>

          <section class="card" style="padding: 0; overflow: auto">
            <h2 style="padding: 14px 20px 8px">Journal des mouvements (24 h)</h2>
            <table class="data">
              <thead><tr><th>Heure</th><th>VIN</th><th>Type</th><th>De → vers</th><th>Agent</th><th>Trace</th></tr></thead>
              <tbody>
                @for (m of journal().slice(0, 40); track m.id) {
                  <tr>
                    <td class="mono">{{ m.startedAt | date: 'HH:mm' }}</td>
                    <td class="mono strong">{{ m.shortCode }}</td>
                    <td>{{ typeLabel(m.type) }}</td>
                    <td class="mono">{{ m.fromPlace ?? '—' }} → {{ m.toPlace ?? '?' }}</td>
                    <td>{{ m.agent }}</td>
                    <td><span class="trace" [class.ko]="!m.traceComplete">{{ m.traceComplete ? 'Complète' : 'Arrivée manquante' }}</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        </div>

        <aside class="col" style="gap: 16px">
          <section class="card col">
            <h2>À régulariser</h2>
            @for (m of missing(); track m.id) {
              <div class="todo">
                <div class="row"><strong class="mono">{{ m.vin }}</strong><span class="grow"></span><span class="muted">{{ m.startedAt | date: 'HH:mm' }}</span></div>
                <span>Parti de <strong>{{ m.fromPlace }}</strong> sans scan d'arrivée · {{ m.agent }}</span>
                @if (sent()[m.id]) {
                  <span class="sent" role="status">Tâche envoyée à l'agent le plus proche</span>
                } @else {
                  <button type="button" class="btn sm" (click)="verify(m)">Demander une vérification</button>
                }
              </div>
            } @empty { <span class="muted">Aucun scan manquant.</span> }
          </section>
          <section class="card col">
            <h2>Agents en service</h2>
            @for (a of agents(); track a.agent) {
              <div class="row agent"><span class="avatar">{{ initials(a.agent) }}</span><span class="grow">{{ a.agent }}</span><span class="muted">{{ a.movements }} mvts</span></div>
            }
          </section>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: 16px; align-items: start; }
    .value.ok { color: var(--ok); } .value.danger { color: var(--danger); }
    .chart { width: 100%; height: auto; }
    .axis { font-size: 11px; fill: var(--muted); }
    .peak { font-size: 12px; font-weight: 600; fill: var(--text); }
    .strong { font-weight: 600; }
    .trace { font-weight: 600; font-size: 13px; color: var(--ok); }
    .trace.ko { color: var(--danger); }
    .todo { border: 1px solid #ebc9c4; background: var(--danger-bg); border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 6px; }
    .sent { font-size: 12px; font-weight: 600; color: var(--ok-ink); }
    .agent { border-top: 1px solid var(--ground-2); padding-top: 8px; }
    .avatar { width: 30px; height: 30px; border-radius: 50%; background: #e4e0d6; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; }
    @media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } }
  `],
})
export class MouvementsComponent implements OnInit {
  private api = inject(ApiService);
  stats = signal<MovementStats | null>(null);
  journal = signal<MovementDto[]>([]);
  missing = signal<MovementDto[]>([]);
  agents = signal<AgentActivity[]>([]);
  sent = signal<Record<number, boolean>>({});

  readonly chartW = 620;
  readonly chartH = 140;

  private max = computed(() => {
    const vals = Object.values(this.stats()?.perHour ?? {});
    const m = Math.max(10, ...vals);
    return Math.ceil(m / 10) * 10;
  });

  bars = computed(() => {
    const entries = Object.entries(this.stats()?.perHour ?? {});
    const bw = (this.chartW - 30) / Math.max(1, entries.length);
    const peak = Math.max(0, ...entries.map(([, v]) => v));
    return entries.map(([label, v], i) => {
      const h = (v / this.max()) * this.chartH;
      return { label, v, x: i * bw + 6, w: bw - 12, h, y: this.chartH - h, peak: v === peak && v > 0 };
    });
  });

  gridLines = computed(() => [0, 0.5, 1].map((f) => ({ v: Math.round(this.max() * f), y: this.chartH - f * this.chartH })));

  chartLabel = computed(() => {
    const b = this.bars().reduce((a, c) => (c.v > a.v ? c : a), { v: 0, label: '—' } as { v: number; label: string });
    return `Mouvements par heure aujourd'hui, pic de ${b.v} à ${b.label}`;
  });

  ngOnInit(): void {
    this.api.movementStats().subscribe((s) => this.stats.set(s));
    this.api.movements(24).subscribe((j) => this.journal.set(j));
    this.api.missingArrivals().subscribe((m) => this.missing.set(m));
    this.api.agents().subscribe((a) => this.agents.set(a));
  }

  verify(m: MovementDto): void {
    this.api.createTask(m.vin, m.fromPlace, `Déplacement depuis ${m.fromPlace} sans scan d'arrivée`).subscribe(() =>
      this.sent.update((s) => ({ ...s, [m.id]: true })),
    );
  }

  typeLabel(t: string): string {
    return TYPE_LABELS[t] ?? t;
  }

  initials(name: string): string {
    return name.split(/[\s.]+/).filter(Boolean).map((p) => p[0]).join('').slice(0, 2).toUpperCase();
  }
}
