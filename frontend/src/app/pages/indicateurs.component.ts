import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { IndicatorSeries } from '../core/models';

interface Spark { points: string; refY: number; last: { x: number; y: number }; dots: { x: number; y: number; label: string }[]; }

/** Module Indicateurs du pilote (superviseur). */
@Component({
  selector: 'dat-indicateurs',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><h1>Indicateurs du pilote</h1><div class="sub">Zone B · comparaison avec la situation de référence mesurée avant le pilote</div></div>
      </div>
      @if (fictitious()) {
        <div class="banner warn" role="note"><strong>Valeurs fictives</strong> pour illustrer le tableau de bord : elles seront remplacées par les mesures réelles du pilote.</div>
      }
      <div class="grid">
        @for (s of series(); track s.code) {
          <section class="card col">
            <h2>{{ s.title }}</h2>
            <div class="row"><span class="big">{{ format(s, s.current) }}</span><span class="delta">✓ {{ s.delta }}</span></div>
            <span class="muted small">Réf. avant pilote : {{ format(s, s.reference) }} · semaine {{ s.weeks.length }}</span>
            @if (spark(s); as sp) {
              <svg viewBox="0 0 290 110" class="spark" role="img" [attr.aria-label]="s.title + ', par semaine du pilote'">
                <line x1="8" x2="248" [attr.y1]="sp.refY" [attr.y2]="sp.refY" stroke="#8A93A0" stroke-width="1.5" stroke-dasharray="4 4" />
                <text x="252" [attr.y]="sp.refY + 4" class="axis">réf.</text>
                <polyline [attr.points]="sp.points" fill="none" stroke="#B94E12" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
                <circle [attr.cx]="sp.last.x" [attr.cy]="sp.last.y" r="5" fill="#B94E12" stroke="#fff" stroke-width="2" />
                @for (d of sp.dots; track $index) {
                  <circle [attr.cx]="d.x" [attr.cy]="d.y" r="10" fill="transparent"><title>{{ d.label }}</title></circle>
                  <text [attr.x]="d.x" y="108" text-anchor="middle" class="axis">S{{ $index + 1 }}</text>
                }
              </svg>
            }
            <span class="method">Mesure : {{ s.method }}</span>
          </section>
        }
      </div>
      <div class="grid3">
        <section class="card col"><h2>Périmètre du pilote</h2>
          <ol><li>Une zone limitée : zone B, 4 allées, 96 places</li><li>Smartphones et QR codes sur chaque place</li><li>Durée : [À CONFIRMER]</li><li>Sans intégration au départ</li></ol></section>
        <section class="card col"><h2>Méthode de mesure</h2>
          <ol><li>Référence mesurée avant le démarrage</li><li>Recherches chronométrées chaque semaine</li><li>Inventaire tournant : scans vs positions enregistrées</li><li>Zone témoin sans l'application</li></ol></section>
        <section class="card dark col"><h2>Après le pilote</h2>
          <ol><li>Intégration avec les systèmes du terminal</li><li>Plateformes de formalités, selon les accès</li><li>RFID si le besoin de lecture automatique est confirmé</li><li>Extension progressive aux autres zones</li></ol></section>
      </div>
    </div>
  `,
  styles: [`
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; }
    .grid3 { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; }
    h2 { font-size: 14px; min-height: 36px; }
    .big { font-family: var(--display); font-size: 32px; font-weight: 800; }
    .delta { font-size: 13px; font-weight: 600; color: var(--ok-ink); background: var(--ok-bg); border-radius: 999px; padding: 2px 8px; }
    .spark { width: 100%; height: auto; }
    .axis { font-size: 10px; fill: var(--muted); }
    .method { font-size: 12px; color: var(--text-2); border-top: 1px solid var(--ground-2); padding-top: 8px; }
    .small { font-size: 12px; }
    ol { margin: 0; padding-left: 20px; line-height: 1.8; }
  `],
})
export class IndicateursComponent implements OnInit {
  private api = inject(ApiService);
  private decimal = new DecimalPipe('fr');
  series = signal<IndicatorSeries[]>([]);
  fictitious = signal(true);

  ngOnInit(): void {
    this.api.indicators().subscribe((i) => {
      this.series.set(i.series);
      this.fictitious.set(i.fictitious);
    });
  }

  format(s: IndicatorSeries, v: number): string {
    if (s.unit === '%') return `${this.decimal.transform(v, '1.1-1')} %`;
    if (v >= 60) return `${Math.floor(v / 60)} h ${String(Math.round(v % 60)).padStart(2, '0')}`;
    return `${this.decimal.transform(v, '1.0-1')} min`;
  }

  spark(s: IndicatorSeries): Spark | null {
    if (!s.weeks.length) return null;
    const all = [...s.weeks, s.reference];
    let lo = Math.min(...all);
    let hi = Math.max(...all);
    const pad = (hi - lo) * 0.15 || 1;
    lo -= pad;
    hi += pad;
    const y = (v: number) => 110 - 18 - ((v - lo) / (hi - lo)) * 80;
    const x = (i: number) => 8 + (i * 240) / Math.max(1, s.weeks.length - 1);
    const dots = s.weeks.map((v, i) => ({ x: x(i), y: y(v), label: `Semaine ${i + 1} : ${this.format(s, v)}` }));
    return {
      points: dots.map((d) => `${d.x.toFixed(1)},${d.y.toFixed(1)}`).join(' '),
      refY: y(s.reference),
      last: dots[dots.length - 1],
      dots,
    };
  }
}
