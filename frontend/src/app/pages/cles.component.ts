import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { KeyStatus } from '../core/models';

const LABELS: Record<string, string> = { ARMOIRE: 'Dans l\'armoire', SORTIE: 'Sortie', REMISE: 'Remise au client', INCONNU: 'Non enregistrée' };

/** Armoire à clés : chaque clé porte le QR « KEY:<VIN> » ; on sait qui l'a et pour quel véhicule. */
@Component({
  selector: 'dat-cles',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><h1>Armoire à clés</h1>
          <div class="sub">Qui détient la clé, pour quel véhicule, depuis quand. Contrôlé aussi au portail de sortie.</div></div>
        <span class="pill">Données fictives de démonstration</span>
      </div>

      <div class="tiles">
        @for (s of states; track s) {
          <button type="button" class="tile" [class.on]="filter() === s" (click)="filter.set(filter() === s ? '' : s)">
            <span class="label">{{ label(s) }}</span><span class="value">{{ count(s) }}</span>
          </button>
        }
      </div>

      @if (auth.can('CLE_MOUVEMENTER')) {
      <form class="card row wrap" (ngSubmit)="scan()">
        <strong>Scan de clé</strong>
        <label class="sr-only" for="kq">QR de la clé</label>
        <input id="kq" name="kq" class="field mono" [(ngModel)]="scanCode" placeholder="KEY:VF3LCYHZPJS218456 ou DAT-218456">
        <select class="field" name="act" [(ngModel)]="scanAction" aria-label="Action">
          <option value="SORTIE">Sortie de l'armoire</option>
          <option value="DEPOSEE">Dépôt dans l'armoire</option>
          <option value="REMISE_CLIENT">Remise au client</option>
        </select>
        <input class="field" name="holder" [(ngModel)]="holder" placeholder="Détenteur (par défaut : vous)">
        <button type="submit" class="btn dark" [disabled]="!scanCode.trim()">Enregistrer</button>
      </form>
      }
      @if (message(); as m) { <div class="banner" [class.ok]="m.ok" [class.danger]="!m.ok" role="status">{{ m.text }}</div> }

      <div class="card" style="padding: 0; overflow: auto">
        <table class="data">
          <thead><tr><th>Véhicule</th><th>Place</th><th>État de la clé</th><th>Détenteur / crochet</th><th>Depuis</th><th></th></tr></thead>
          <tbody>
            @for (k of rows(); track k.vin) {
              <tr>
                <td><strong class="mono">{{ k.shortCode }}</strong> <span class="muted">{{ k.model }}</span></td>
                <td class="mono">{{ k.placeLabel }}</td>
                <td><span class="state" [attr.data-s]="k.state">{{ label(k.state) }}</span></td>
                <td>{{ k.state === 'ARMOIRE' ? k.slot : (k.holder || '—') }}</td>
                <td>{{ k.since ? (k.since | date: 'dd/MM HH:mm') : '—' }}</td>
                <td class="actions">
                  @if (auth.can('CLE_MOUVEMENTER') && k.state !== 'SORTIE') { <button type="button" class="btn sm" (click)="act(k, 'SORTIE')">Sortir</button> }
                  @if (auth.can('CLE_MOUVEMENTER') && k.state !== 'ARMOIRE') { <button type="button" class="btn sm" (click)="act(k, 'DEPOSEE')">Déposer</button> }
                </td>
              </tr>
            } @empty { <tr><td colspan="6" class="muted">Aucune clé.</td></tr> }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: [`
    .tile { cursor: pointer; text-align: left; font: inherit; }
    .tile.on { border: 2px solid var(--ink); }
    .wrap { flex-wrap: wrap; }
    .state { font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 999px; }
    .state[data-s='ARMOIRE'] { background: var(--ok-bg); color: var(--ok-ink); }
    .state[data-s='SORTIE'] { background: var(--warn-bg); color: var(--warn-ink); }
    .state[data-s='REMISE'] { background: var(--ground-2); color: var(--text-2); }
    .state[data-s='INCONNU'] { background: var(--info-bg); color: var(--info-ink); }
    .actions { display: flex; gap: 6px; }
  `],
})
export class ClesComponent implements OnInit {
  private api = inject(ApiService);
  auth = inject(AuthService);
  readonly states = ['ARMOIRE', 'SORTIE', 'INCONNU'];
  all = signal<KeyStatus[]>([]);
  filter = signal('');
  message = signal<{ ok: boolean; text: string } | null>(null);
  scanCode = '';
  scanAction: 'SORTIE' | 'DEPOSEE' | 'REMISE_CLIENT' = 'SORTIE';
  holder = '';

  rows = computed(() => this.all().filter((k) => !this.filter() || k.state === this.filter()));

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.keys().subscribe((k) => this.all.set(k));
  }

  label(s: string): string {
    return LABELS[s] ?? s;
  }

  count(s: string): number {
    return this.all().filter((k) => k.state === s).length;
  }

  scan(): void {
    const code = this.scanCode.trim().toUpperCase().replace(/^KEY:/, '');
    this.send(code, this.scanAction, this.holder.trim() || undefined);
    this.scanCode = '';
  }

  act(k: KeyStatus, action: 'SORTIE' | 'DEPOSEE'): void {
    this.send(k.vin, action);
  }

  private send(vin: string, action: 'SORTIE' | 'DEPOSEE' | 'REMISE_CLIENT', holder?: string): void {
    this.api.keyAction(vin, action, holder).subscribe({
      next: (r) => {
        this.message.set({ ok: true, text: `Clé de ${r.shortCode} : ${this.label(r.state).toLowerCase()}${r.state === 'ARMOIRE' ? ' · ' + r.slot : ' · ' + (r.holder ?? '')}` });
        this.load();
      },
      error: (e) => this.message.set({ ok: false, text: e?.error?.message ?? 'Action impossible' }),
    });
  }
}
