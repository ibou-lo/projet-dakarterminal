import { DatePipe } from '@angular/common';
import { Component, effect, input, output, signal } from '@angular/core';
import QRCode from 'qrcode';
import { TicketDto } from '../core/models';

/** Fenêtre du ticket de retrait : QR code signé, emplacement, bénéficiaire, validité. */
@Component({
  selector: 'dat-ticket-dialog',
  standalone: true,
  imports: [DatePipe],
  template: `
    <div class="backdrop" (click)="close.emit()" role="presentation">
      <section class="ticket" role="dialog" aria-modal="true" aria-labelledby="tk-title" (click)="$event.stopPropagation()">
        <header class="row">
          <span class="kicker">Ticket de retrait</span>
          <span class="grow"></span>
          <button type="button" class="x" aria-label="Fermer" (click)="close.emit()">✕</button>
        </header>
        <h2 id="tk-title">{{ ticket().ticketNumber }}</h2>
        <span class="badge READY">{{ ticket().used ? 'Ticket utilisé' : 'Prêt à enlever' }}</span>
        <div class="qr">
          @if (qr(); as src) { <img [src]="src" width="220" height="220" alt="QR code du ticket {{ ticket().ticketNumber }}"> }
          @else { <span class="muted">Génération du QR…</span> }
        </div>
        <div class="grid">
          <div><span>Véhicule</span><strong>{{ ticket().model }}</strong></div>
          <div><span>VIN</span><strong class="mono">…{{ ticket().vin.slice(9) }}</strong></div>
          <div><span>Emplacement</span><strong>{{ ticket().placeLabel }}</strong></div>
          <div><span>Bénéficiaire</span><strong>{{ ticket().beneficiary }}</strong></div>
          <div><span>Valable jusqu'au</span><strong>{{ ticket().validUntil | date: 'dd/MM · HH:mm' }}</strong></div>
          <div><span>Créneau</span><strong>{{ ticket().slot ?? '—' }}</strong></div>
        </div>
        <p class="muted small">QR signé (HMAC) · usage unique au portail. Le client le reçoit aussi dans son espace mobile.</p>
        <div class="row">
          <button type="button" class="btn" (click)="print()">Imprimer</button>
          <span class="grow"></span>
          <button type="button" class="btn primary" (click)="close.emit()">Fermer</button>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .backdrop { position: fixed; inset: 0; background: rgba(15, 29, 43, .55); display: flex; align-items: center; justify-content: center; z-index: 100; padding: 16px; }
    .ticket { width: 420px; max-width: 100%; max-height: 100%; overflow: auto; background: #fff; border-radius: 18px; padding: 22px; display: flex; flex-direction: column; gap: 12px; box-shadow: 0 20px 60px rgba(0,0,0,.3); }
    .kicker { font-size: 12px; font-weight: 600; letter-spacing: .8px; text-transform: uppercase; color: var(--muted); }
    .x { border: 0; background: transparent; font-size: 18px; cursor: pointer; width: 36px; height: 36px; border-radius: 8px; }
    .x:hover { background: var(--ground); }
    h2 { font-family: var(--display); font-size: 26px; font-weight: 800; }
    .badge { align-self: flex-start; }
    .qr { display: flex; justify-content: center; padding: 12px; border: 1px solid var(--line); border-radius: 12px; min-height: 244px; align-items: center; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; border-top: 2px dashed var(--line); padding-top: 14px; }
    .grid div { display: flex; flex-direction: column; font-size: 13px; }
    .grid span { color: var(--muted); }
    .small { font-size: 12px; margin: 0; }
  `],
})
export class TicketDialogComponent {
  ticket = input.required<TicketDto>();
  close = output<void>();
  qr = signal<string | null>(null);

  constructor() {
    effect(() => {
      const token = this.ticket().qrToken;
      QRCode.toDataURL(token, { width: 440, margin: 1, color: { dark: '#0F1D2B', light: '#FFFFFF' } })
        .then((url: string) => this.qr.set(url))
        .catch(() => this.qr.set(null));
    }, { allowSignalWrites: true });
  }

  print(): void {
    window.print();
  }
}
