import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ZoneStore } from '../core/zone.store';
import { PlaceLabel, TagDto } from '../core/models';
import { QrComponent } from '../shared/qr.component';

/**
 * Étiquette hybride QR + RFID : association de la puce au passeport,
 * impression des étiquettes véhicules (VH:<VIN>) et des QR de places (PL:…).
 */
@Component({
  selector: 'dat-etiquettes',
  standalone: true,
  imports: [FormsModule, QrComponent],
  template: `
    <div class="page">
      <div class="page-head no-print">
        <div class="grow"><h1>Étiquettes QR + RFID</h1>
          <div class="sub">Une étiquette, deux lectures : QR lu au smartphone, puce RFID lue par les portiques. Variante suspendue réutilisable.</div></div>
        <button type="button" class="btn dark" (click)="print()" [disabled]="!printable()">Imprimer ({{ printCount() }})</button>
      </div>

      <div class="row no-print">
        <button type="button" class="chip" [attr.aria-pressed]="tab() === 'vehicles'" (click)="tab.set('vehicles')">Étiquettes véhicules</button>
        <button type="button" class="chip" [attr.aria-pressed]="tab() === 'places'" (click)="tab.set('places')">QR des places</button>
      </div>

      @if (tab() === 'vehicles') {
        <div class="layout no-print">
          <div class="col" style="gap: 12px">
            <div class="row">
              <label class="sr-only" for="tq">Rechercher</label>
              <input id="tq" class="field" placeholder="VIN ou code DAT…" [(ngModel)]="q" (keyup.enter)="load()">
              <select class="field" [(ngModel)]="zone" (change)="load()" aria-label="Zone">
                <option value="">Toutes zones</option>
                @for (z of zoneStore.codes(); track z) { <option [value]="z">Zone {{ z }}</option> }
              </select>
              <span class="grow"></span>
              <button type="button" class="btn sm" (click)="selectAll()">Tout sélectionner</button>
            </div>
            <div class="card" style="padding: 0; overflow: auto; max-height: 560px">
              <table class="data">
                <thead><tr><th></th><th>Véhicule</th><th>Place</th><th>Puce RFID (EPC)</th><th>Type</th><th></th></tr></thead>
                <tbody>
                  @for (t of tags(); track t.vin) {
                    <tr [class.selected]="t.vin === editing()?.vin">
                      <td><input type="checkbox" [checked]="picked().has(t.vin)" (change)="toggle(t.vin)" [attr.aria-label]="'Imprimer ' + t.shortCode"></td>
                      <td><strong class="mono">{{ t.shortCode }}</strong> <span class="muted">{{ t.model }}</span></td>
                      <td class="mono">{{ t.placeLabel }}</td>
                      <td class="mono">{{ t.epc || '— non activée' }}</td>
                      <td>{{ t.reusable ? 'Suspendue (réutilisable)' : 'Autocollant' }}</td>
                      <td>@if (auth.can('ETIQUETTE_ASSOCIER')) { <button type="button" class="btn sm" (click)="edit(t)">Associer une puce</button> }</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>

          <aside class="col" style="gap: 12px">
            @if (editing(); as t) {
              <form class="card col" (ngSubmit)="saveTag(t)">
                <h2>Puce RFID de {{ t.shortCode }}</h2>
                <label class="col">EPC lu par le lecteur RFID
                  <input class="field mono" name="epc" [(ngModel)]="epc" placeholder="E280-…" required>
                </label>
                <label class="row check"><input type="checkbox" name="reu" [(ngModel)]="reusable"> Étiquette suspendue réutilisable (récupérée au portail)</label>
                <button type="submit" class="btn primary" [disabled]="!epc.trim()">Associer au passeport</button>
              </form>
            }
            <section class="card col">
              <h2>Déploiement</h2>
              <ol>
                <li><strong>Pilote</strong> : QR seul, lu au smartphone ; la puce est posée mais pas exploitée.</li>
                <li><strong>Si besoin confirmé</strong> : portiques RFID aux entrées de zone et au portail.</li>
                <li><strong>Extension</strong> : lecteur embarqué, armoire à clés.</li>
              </ol>
            </section>
            @if (message(); as m) { <div class="banner" [class.ok]="m.ok" [class.danger]="!m.ok">{{ m.text }}</div> }
          </aside>
        </div>
      } @else {
        <div class="row no-print">
          <select class="field" [(ngModel)]="placeZone" (change)="loadPlaces()" aria-label="Zone">
            @for (z of zoneStore.codes(); track z) { <option [value]="z">Zone {{ z }}</option> }
          </select>
          <select class="field" [(ngModel)]="placeAllee" (change)="loadPlaces()" aria-label="Allée">
            <option [ngValue]="null">Toutes les allées</option>
            @for (a of [1, 2, 3, 4]; track a) { <option [ngValue]="a">Allée {{ a }}</option> }
          </select>
          <span class="muted">{{ placeLabels().length }} QR de places</span>
        </div>
      }

      <!-- Aperçu / impression -->
      <section class="sheet" [class.places]="tab() === 'places'">
        @if (tab() === 'vehicles') {
          @for (t of selectedTags(); track t.vin) {
            <article class="label">
              <dat-qr [data]="t.vehicleQr" [size]="110" />
              <div class="col" style="gap: 3px">
                <span class="brand">Dakar AutoTrack</span>
                <span class="mono vin">{{ t.vin }}</span>
                <span class="muted small">{{ t.model }}</span>
                <strong class="code">{{ t.shortCode }}</strong>
                <span class="small mono">{{ t.epc ? 'RFID ' + t.epc : 'RFID non activée' }}</span>
              </div>
              <div class="key col">
                <dat-qr [data]="t.keyQr" [size]="56" />
                <span class="small">Clé</span>
              </div>
            </article>
          } @empty { <p class="muted no-print">Cochez des véhicules pour préparer leurs étiquettes.</p> }
        } @else {
          @for (p of placeLabels(); track p.code) {
            <article class="plabel">
              <dat-qr [data]="p.qrPayload" [size]="100" />
              <strong>{{ p.label }}</strong>
            </article>
          }
        }
      </section>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px; align-items: start; }
    h2 { font-size: 15px; }
    label.col { font-size: 12px; font-weight: 600; gap: 4px; }
    .check { font-size: 13px; gap: 8px; }
    ol { margin: 0; padding-left: 18px; line-height: 1.7; font-size: 13px; }
    .sheet { display: grid; grid-template-columns: repeat(auto-fill, minmax(330px, 1fr)); gap: 12px; }
    .sheet.places { grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); }
    .label { background: #fff; border: 1px solid var(--line-2); border-radius: 12px; padding: 12px; display: flex; gap: 12px; align-items: center; break-inside: avoid; }
    .brand { font-family: var(--display); font-weight: 800; font-size: 13px; }
    .vin { font-size: 12px; font-weight: 600; }
    .code { font-family: var(--display); font-size: 22px; }
    .small { font-size: 11px; }
    .key { margin-left: auto; align-items: center; gap: 2px; border-left: 1px dashed var(--line-2); padding-left: 10px; }
    .plabel { background: #fff; border: 1px solid var(--line-2); border-radius: 10px; padding: 10px; display: flex; flex-direction: column; align-items: center; gap: 6px; break-inside: avoid; }
    @media print {
      .no-print { display: none !important; }
      .sheet { gap: 6mm; }
      .label, .plabel { border-color: #999; }
    }
  `],
})
export class EtiquettesComponent implements OnInit {
  private api = inject(ApiService);
  auth = inject(AuthService);
  zoneStore = inject(ZoneStore);
  tab = signal<'vehicles' | 'places'>('vehicles');
  tags = signal<TagDto[]>([]);
  picked = signal<Set<string>>(new Set(['VF3LCYHZPJS218456']));
  editing = signal<TagDto | null>(null);
  placeLabels = signal<PlaceLabel[]>([]);
  message = signal<{ ok: boolean; text: string } | null>(null);
  q = '';
  zone = 'B';
  placeZone = 'B';
  placeAllee: number | null = 3;
  epc = '';
  reusable = false;

  selectedTags = computed(() => this.tags().filter((t) => this.picked().has(t.vin)));
  printCount = computed(() => (this.tab() === 'vehicles' ? this.selectedTags().length : this.placeLabels().length));
  printable = computed(() => this.printCount() > 0);

  ngOnInit(): void {
    this.zoneStore.load();
    this.load();
    this.loadPlaces();
  }

  load(): void {
    this.api.tags(this.zone, this.q.trim()).subscribe((t) => this.tags.set(t));
  }

  loadPlaces(): void {
    this.api.placeLabels(this.placeZone, this.placeAllee).subscribe((p) => this.placeLabels.set(p));
  }

  toggle(vin: string): void {
    this.picked.update((s) => {
      const n = new Set(s);
      if (n.has(vin)) n.delete(vin); else n.add(vin);
      return n;
    });
  }

  selectAll(): void {
    this.picked.set(new Set(this.tags().map((t) => t.vin)));
  }

  edit(t: TagDto): void {
    this.editing.set(t);
    this.epc = t.epc ?? '';
    this.reusable = t.reusable;
  }

  saveTag(t: TagDto): void {
    this.api.associateTag(t.vin, this.epc.trim(), this.reusable).subscribe({
      next: (r) => {
        this.message.set({ ok: true, text: `Puce ${r.epc} associée à ${r.shortCode}` });
        this.editing.set(null);
        this.load();
      },
      error: (e) => this.message.set({ ok: false, text: e?.error?.message ?? 'Association impossible' }),
    });
  }

  print(): void {
    window.print();
  }
}
