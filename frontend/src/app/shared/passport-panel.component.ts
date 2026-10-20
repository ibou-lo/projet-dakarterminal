import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { Passport, TicketDto } from '../core/models';
import { TicketDialogComponent } from './ticket-dialog.component';

/** Passeport numérique : statut, place, formalités, historique, actions. */
@Component({
  selector: 'dat-passport-panel',
  standalone: true,
  imports: [DatePipe, TicketDialogComponent, RouterLink],
  template: `
    @if (passport(); as p) {
      <aside class="panel" aria-label="Passeport numérique du véhicule">
        <div class="col" style="gap: 4px">
          <div class="row">
            <span class="kicker">Passeport numérique</span><span class="grow"></span>
            <span class="muted small">Escale {{ p.vehicle.escale }} · {{ p.vehicle.daysOnPark }} j sur parc</span>
          </div>
          <div class="vin mono">{{ p.vehicle.vin }}</div>
          <div class="muted">{{ p.vehicle.model }} · {{ p.vehicle.modelYear }} · {{ p.vehicle.color }} · BL {{ p.vehicle.blNumber }}</div>
        </div>

        @switch (p.vehicle.status) {
          @case ('READY') { <div class="banner ok" role="status"><strong>Prêt à enlever</strong><span>Localisé et autorisé à sortir</span></div> }
          @case ('AWAITING_AUTHORIZATION') { <div class="banner warn" role="status"><strong>Véhicule localisé</strong><span>Autorisation de sortie en attente</span></div> }
          @case ('POSITION_TO_CONFIRM') { <div class="banner info" role="status"><strong>Position à confirmer</strong><span>Déplacement sans scan d'arrivée</span></div> }
          @default { <div class="banner" role="status"><strong>Sorti du terminal</strong></div> }
        }

        <div class="place">
          @for (part of placeParts(); track $index) {
            <div class="box" [class.hl]="$index === 2"><span>{{ ['Zone', 'Allée', 'Place'][$index] }}</span><strong>{{ part }}</strong></div>
          }
        </div>

        @if (p.vehicle.status !== 'EXITED' && auth.can('VEHICULE_STATIONNER') && auth.canAccess('/affectation')) {
          <a class="btn sm" [routerLink]="['/affectation']" [queryParams]="{ vin: p.vehicle.vin }">
            {{ p.vehicle.placeCode ? 'Déplacer / réaffecter' : 'Affecter une place' }}
          </a>
        }

        <section class="col">
          <h3>Formalités d'enlèvement</h3>
          @for (f of p.formalities; track f.type) {
            <div class="frow">
              <span class="ico" [class.ok]="f.status === 'RECEIVED'" aria-hidden="true">{{ f.status === 'RECEIVED' ? '✓' : '◷' }}</span>
              <span class="grow">{{ f.label }}</span>
              <span class="st" [class.ok]="f.status === 'RECEIVED'">
                {{ f.status === 'RECEIVED' ? ('Reçu ' + (f.receivedAt | date: 'dd/MM HH:mm')) : 'En attente' }}
              </span>
            </div>
          }
        </section>

        <section class="col hist">
          <h3>Historique</h3>
          @for (h of p.history.slice(-5); track $index) {
            <div class="hrow">
              <span class="mono t">{{ h.at | date: 'dd/MM HH:mm' }}</span>
              <div class="col" style="gap: 0"><strong>{{ h.what }}</strong><span class="muted">{{ h.who }}</span></div>
            </div>
          }
        </section>

        @if (p.ticket; as t) {
          <div class="banner ok small">Ticket <strong class="mono">{{ t.ticketNumber }}</strong> · valable jusqu'au {{ t.validUntil | date: 'dd/MM HH:mm' }}</div>
        }
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }

        <div class="row actions">
          @if (auth.can('FORMALITE_SIMULER') && p.vehicle.status !== 'READY' && p.vehicle.status !== 'EXITED' && baePending()) {
            <button type="button" class="btn dark grow" (click)="simulateBae(p.vehicle.vin)" [disabled]="busy()">Simuler la réception du BAE</button>
          }
          @if (auth.can('FORMALITE_SIMULER') && !baePending()) {
            <button type="button" class="btn" (click)="reset(p.vehicle.vin)" [disabled]="busy()">Réinitialiser</button>
          }
          <button type="button" class="btn primary grow" [disabled]="(p.vehicle.status !== 'READY' && !p.ticket) || (!p.ticket && !auth.can('TICKET_EMETTRE')) || busy()" (click)="p.ticket ? shown.set(p.ticket) : ticket(p.vehicle.vin)">
            {{ p.ticket ? 'Voir le ticket' : 'Générer le ticket de retrait' }}
          </button>
        </div>
      </aside>
      @if (shown(); as t) { <dat-ticket-dialog [ticket]="t" (close)="shown.set(null)" /> }
    }
  `,
  styles: [`
    .panel { background: #fff; border: 1px solid var(--line); border-radius: var(--radius); padding: 20px 22px; display: flex; flex-direction: column; gap: 14px; }
    .kicker { font-size: 12px; font-weight: 600; letter-spacing: .8px; text-transform: uppercase; color: var(--rust); }
    .small { font-size: 12px; }
    .vin { font-size: 21px; font-weight: 600; letter-spacing: .5px; }
    .banner { flex-direction: column; align-items: flex-start; gap: 2px; }
    .place { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .box { border: 1px solid var(--line); border-radius: 10px; padding: 8px 12px; display: flex; flex-direction: column; }
    .box span { font-size: 12px; color: var(--muted); }
    .box strong { font-family: var(--display); font-size: 22px; }
    .box.hl { border: 2px solid var(--orange); }
    h3 { font-size: 14px; font-weight: 600; }
    .frow { display: flex; gap: 10px; align-items: center; min-height: 30px; border-bottom: 1px solid var(--ground-2); }
    .ico { width: 18px; color: var(--warn); font-weight: 700; }
    .ico.ok { color: var(--ok); }
    .st { font-size: 12px; font-weight: 600; color: var(--warn); }
    .st.ok { color: var(--ok); }
    .hrow { display: flex; gap: 12px; font-size: 13px; }
    .t { width: 84px; flex-shrink: 0; color: var(--muted); font-size: 12px; }
    .actions { flex-wrap: wrap; }
  `],
})
export class PassportPanelComponent {
  api = inject(ApiService);
  auth = inject(AuthService);
  passport = input<Passport | null>(null);
  changed = output<Passport>();
  ticketIssued = output<TicketDto>();
  busy = signal(false);
  error = signal<string | null>(null);
  /** Ticket affiché dans la fenêtre (null = fermée). */
  shown = signal<TicketDto | null>(null);

  placeParts = computed(() => {
    const code = this.passport()?.vehicle.placeCode;
    return code && code !== 'SORTI' ? code.split('-') : ['—', '—', '—'];
  });

  baePending = computed(() => this.passport()?.formalities.some((f) => f.type === 'BAE' && f.status === 'PENDING') ?? false);

  simulateBae(vin: string): void {
    this.run(this.api.receiveFormality(vin, 'BAE'));
  }

  reset(vin: string): void {
    this.run(this.api.resetFormality(vin, 'BAE'));
  }

  ticket(vin: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.issueTicket(vin).subscribe({
      next: (t) => {
        this.busy.set(false);
        this.shown.set(t);
        this.ticketIssued.emit(t);
        this.api.passport(vin).subscribe((p) => this.changed.emit(p));
      },
      error: (e) => this.fail(e),
    });
  }

  private run(obs: ReturnType<ApiService['passport']>): void {
    this.busy.set(true);
    this.error.set(null);
    obs.subscribe({
      next: (p) => {
        this.busy.set(false);
        this.changed.emit(p);
      },
      error: (e) => this.fail(e),
    });
  }

  private fail(e: { status?: number; error?: { message?: string } }): void {
    this.busy.set(false);
    if (e?.status === 0) {
      this.error.set('API injoignable : vérifiez que le backend tourne sur le port 8080');
    } else {
      this.error.set(`${e?.error?.message ?? 'Action impossible'}${e?.status ? ' (HTTP ' + e.status + ')' : ''}`);
    }
    console.error('[AutoTrack] action refusée', e);
  }
}
