import { Component, computed, input, output } from '@angular/core';
import { PlaceCell, ZoneMap } from '../core/models';

/**
 * Plan d'une zone : une case par place, colorée selon le statut (parc) ou
 * selon l'ancienneté de la dernière lecture (carte « fraîcheur »).
 */
@Component({
  selector: 'dat-zone-grid',
  standalone: true,
  template: `
    <div class="zone" [class.focus]="focus()">
      <div class="head">
        <span class="letter">{{ zone().zone }}</span>
        <span class="label">{{ zone().label }} · {{ zone().occupied }}/{{ zone().total }} places</span>
        <span class="grow"></span>
        @if (mode() === 'freshness') {
          <span class="score" [class.good]="zone().freshPct >= 70">{{ zone().freshPct }} % lu en moins de 6 h</span>
        }
      </div>
      @for (allee of zone().allees; track $index) {
        <div class="allee">
          <span class="al" [class.hl]="isSelectedAllee(allee)">A{{ allee[0].allee }}</span>
          <div class="slots">
            @for (p of allee; track p.code) {
              <button type="button" class="slot" [attr.data-kind]="kind(p)" [class.sel]="p.vin && p.vin === selectedVin()"
                      [class.incoh]="p.incoherent" [class.target]="p.code === selectedPlace()"
                      [class.pickable]="selectable() && !p.vin" [attr.aria-label]="tip(p)" [title]="tip(p)"
                      [disabled]="selectable() ? !!p.vin : !p.vin" (click)="onClick(p)">
                @if ((p.vin && p.vin === selectedVin()) || p.code === selectedPlace()) {
                  <span class="tag">{{ p.code.replaceAll('-', ' · ') }}</span>
                }
              </button>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .zone { background: #fbfaf6; border: 1px solid var(--line); border-radius: 10px; padding: 12px 14px; display: flex; flex-direction: column; gap: 6px; }
    .zone.focus { border: 2px solid var(--ink); }
    .head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 4px; }
    .letter { font-family: var(--display); font-size: 20px; font-weight: 800; }
    .label { font-size: 12px; color: var(--text-2); }
    .grow { flex: 1; }
    .score { font-size: 12px; font-weight: 600; color: var(--warn-ink); }
    .score.good { color: var(--ok-ink); }
    .allee { display: flex; align-items: center; gap: 8px; }
    .al { width: 26px; font-size: 11px; font-weight: 600; color: var(--muted); }
    .al.hl { color: var(--rust); }
    .slots { display: flex; gap: 3px; flex-wrap: wrap; row-gap: 4px; min-width: 0; }
    .slot { position: relative; width: 14px; height: 22px; border-radius: 3px; padding: 0; border: 1px dashed #a49d8e; background: transparent; cursor: default; }
    .slot:not(:disabled) { cursor: pointer; }
    .slot[data-kind='OCCUPIED'] { background: var(--slot-occupied); border: 1px solid var(--slot-occupied); }
    .slot[data-kind='READY'] { background: var(--slot-ready); border: 1px solid var(--slot-ready); }
    .slot[data-kind='FRESH_1H'] { background: var(--fresh-1); border: 1px solid var(--fresh-1); }
    .slot[data-kind='H1_6'] { background: var(--fresh-2); border: 1px solid var(--fresh-2); }
    .slot[data-kind='H6_24'] { background: var(--fresh-3); border: 1px solid var(--fresh-3); }
    .slot[data-kind='OLD'], .slot[data-kind='NEVER'] { background: var(--fresh-4); border: 1px solid #b7c4d4; }
    .slot.incoh { border: 2.5px solid #c0392b; }
    .slot.sel { background: var(--orange) !important; border: 1px solid var(--ink) !important; box-shadow: 0 0 0 3px rgba(217, 96, 26, .35); }
    .slot.pickable { border: 1px dashed var(--ok); cursor: pointer; }
    .slot.pickable:hover { background: var(--ok-bg); }
    .slot.target { background: var(--ok) !important; border: 1px solid var(--ink) !important; box-shadow: 0 0 0 3px rgba(23, 113, 74, .35); }
    .tag { position: absolute; left: 50%; bottom: 30px; transform: translateX(-50%); white-space: nowrap; background: var(--ink); color: #fff; font-family: var(--mono); font-size: 12px; font-weight: 600; padding: 3px 8px; border-radius: 6px; pointer-events: none; }
  `],
})
export class ZoneGridComponent {
  zone = input.required<ZoneMap>();
  mode = input<'status' | 'freshness'>('status');
  selectedVin = input<string | null>(null);
  focus = input(false);
  pick = output<string>();
  /** Mode affectation : les places LIBRES deviennent cliquables. */
  selectable = input(false);
  selectedPlace = input<string | null>(null);
  placePick = output<string>();

  onClick(p: PlaceCell): void {
    if (this.selectable()) {
      if (!p.vin) this.placePick.emit(p.code);
    } else if (p.vin) {
      this.pick.emit(p.vin);
    }
  }

  private selectedAllee = computed(() => {
    const vin = this.selectedVin();
    for (const a of this.zone().allees) if (a.some((p) => p.vin && p.vin === vin)) return a[0]?.allee ?? null;
    return null;
  });

  isSelectedAllee(allee: PlaceCell[]): boolean {
    return allee[0]?.allee === this.selectedAllee();
  }

  kind(p: PlaceCell): string {
    if (!p.vin) return 'FREE';
    return this.mode() === 'freshness' ? p.freshness : p.state;
  }

  tip(p: PlaceCell): string {
    const base = p.code.replaceAll('-', ' · ');
    if (!p.vin) return `${base} · libre`;
    const read = p.lastReadAt ? ` · lu ${new Date(p.lastReadAt).toLocaleString('fr-FR')} (${p.lastReadSource})` : '';
    return `${base} · ${p.shortCode}${read}${p.incoherent ? ' · incohérence' : ''}`;
  }
}
