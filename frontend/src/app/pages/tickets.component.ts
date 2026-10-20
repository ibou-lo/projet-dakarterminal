import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { TicketDto } from '../core/models';
import { TicketDialogComponent } from '../shared/ticket-dialog.component';

type TicketState = 'ACTIVE' | 'USED' | 'EXPIRED';

/** Consultation des tickets de retrait émis (QR, validité, utilisation au portail). */
@Component({
  selector: 'dat-tickets',
  standalone: true,
  imports: [DatePipe, FormsModule, TicketDialogComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><h1>Tickets de retrait</h1>
          <div class="sub">QR signé, valable 48 h, utilisable une seule fois au portail</div></div>
        <span class="pill">Données fictives de démonstration</span>
      </div>

      <div class="tiles">
        <div class="tile"><span class="label">Actifs</span><span class="value ok">{{ count('ACTIVE') }}</span></div>
        <div class="tile"><span class="label">Utilisés (véhicule sorti)</span><span class="value">{{ count('USED') }}</span></div>
        <div class="tile"><span class="label">Expirés sans passage</span><span class="value warn">{{ count('EXPIRED') }}</span></div>
      </div>

      <div class="row filters">
        <label class="sr-only" for="tq">Rechercher un ticket</label>
        <input id="tq" class="field" style="width: 280px" placeholder="N° de ticket, VIN, code DAT…" [(ngModel)]="q">
        @for (c of chips; track c.key) {
          <button type="button" class="chip" [attr.aria-pressed]="filter() === c.key" (click)="filter.set(c.key)">{{ c.label }}</button>
        }
      </div>

      <div class="card" style="padding: 0; overflow: auto">
        <table class="data">
          <thead><tr><th>Ticket</th><th>Véhicule</th><th>Emplacement</th><th>Bénéficiaire</th><th>Émis le</th><th>Valable jusqu'au</th><th>État</th><th></th></tr></thead>
          <tbody>
            @for (t of rows(); track t.ticketNumber) {
              <tr class="clickable" (click)="shown.set(t)">
                <td class="mono strong">{{ t.ticketNumber }}</td>
                <td>{{ t.shortCode }} · {{ t.model }}</td>
                <td class="mono">{{ t.placeLabel || '—' }}</td>
                <td>{{ t.beneficiary }}</td>
                <td>{{ t.issuedAt | date: 'dd/MM HH:mm' }}</td>
                <td>{{ t.validUntil | date: 'dd/MM HH:mm' }}</td>
                <td><span class="state" [attr.data-s]="state(t)">{{ stateLabel(t) }}</span></td>
                <td><button type="button" class="btn sm" (click)="shown.set(t); $event.stopPropagation()">Voir le ticket</button></td>
              </tr>
            } @empty {
              <tr><td colspan="8" class="muted">Aucun ticket ne correspond.</td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    </div>
    @if (shown(); as t) { <dat-ticket-dialog [ticket]="t" (close)="shown.set(null)" /> }
  `,
  styles: [`
    .filters { flex-wrap: wrap; }
    .value.ok { color: var(--ok); } .value.warn { color: var(--warn-ink); }
    .strong { font-weight: 600; }
    .clickable { cursor: pointer; }
    .clickable:hover td { background: #fff6ee; }
    .state { font-size: 12px; font-weight: 600; padding: 4px 10px; border-radius: 999px; white-space: nowrap; }
    .state[data-s='ACTIVE'] { background: var(--ok-bg); color: var(--ok-ink); }
    .state[data-s='USED'] { background: var(--ground-2); color: var(--text-2); }
    .state[data-s='EXPIRED'] { background: var(--warn-bg); color: var(--warn-ink); }
  `],
})
export class TicketsComponent implements OnInit {
  private api = inject(ApiService);
  all = signal<TicketDto[]>([]);
  filter = signal<TicketState | ''>('ACTIVE');
  shown = signal<TicketDto | null>(null);
  error = signal<string | null>(null);
  q = '';

  chips: { key: TicketState | ''; label: string }[] = [
    { key: 'ACTIVE', label: 'Actifs' },
    { key: 'USED', label: 'Utilisés' },
    { key: 'EXPIRED', label: 'Expirés' },
    { key: '', label: 'Tous' },
  ];

  rows = computed(() => {
    const f = this.filter();
    return this.all().filter((t) => !f || this.state(t) === f);
  });

  ngOnInit(): void {
    this.api.tickets().subscribe({
      next: (t) => this.all.set(t),
      error: (e) => this.error.set(e?.error?.message ?? 'Chargement impossible'),
    });
  }

  /** Recherche : ouvre directement le ticket correspondant. */
  ngDoCheck(): void {
    const q = this.q.trim().toUpperCase();
    if (q.length < 6) return;
    const hit = this.all().find((t) => t.ticketNumber === q || t.vin === q || t.shortCode === q || t.vin.endsWith(q));
    if (hit && this.shown()?.ticketNumber !== hit.ticketNumber) {
      this.q = '';
      this.shown.set(hit);
    }
  }

  state(t: TicketDto): TicketState {
    if (t.used) return 'USED';
    return new Date(t.validUntil).getTime() < Date.now() ? 'EXPIRED' : 'ACTIVE';
  }

  stateLabel(t: TicketDto): string {
    return { ACTIVE: 'Actif', USED: 'Utilisé', EXPIRED: 'Expiré' }[this.state(t)];
  }

  count(s: TicketState): number {
    return this.all().filter((t) => this.state(t) === s).length;
  }
}
