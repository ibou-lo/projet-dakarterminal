import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { PickupCard, PickupColumn, TicketDto } from '../core/models';
import { TicketDialogComponent } from '../shared/ticket-dialog.component';

const DOTS: Record<string, string> = {
  FORMALITES_EN_COURS: '#D98E1F', AUTORISE_TICKET_A_EMETTRE: '#2E8B5E', TICKET_EMIS: '#1E4E8C', AU_PORTAIL: '#0F1D2B', SORTI: '#8A93A0',
};

/** Module Enlèvements : du dossier à la sortie. */
@Component({
  selector: 'dat-enlevements',
  standalone: true,
  imports: [TicketDialogComponent],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><h1>Enlèvements</h1><div class="sub">De la formalité à la sortie : un véhicule localisé n'est pas encore un véhicule autorisé</div></div>
        <span class="pill">Données fictives de démonstration</span>
      </div>
      @if (message()) { <div class="banner ok" role="status">{{ message() }}</div> }
      <div class="board">
        @for (col of columns(); track col.stage) {
          <section class="column" [attr.aria-label]="col.title">
            <div class="row head"><span class="dot" [style.background]="dot(col.stage)"></span><h2 class="grow">{{ col.title }}</h2><span class="count">{{ col.count }}</span></div>
            <span class="hint">{{ col.hint }}</span>
            @for (c of col.cards; track c.vin) {
              <article class="kcard">
                <div class="row"><span class="mono strong">{{ c.shortCode }}</span><span class="grow"></span><span class="mono muted">{{ c.placeLabel }}</span></div>
                <span class="muted">{{ c.model }}</span>
                <span class="muted small">{{ c.client }}</span>
                @if (c.missing.length) {
                  <div class="row wrap">@for (m of c.missing; track m) { <span class="miss">Manque : {{ m }}</span> }</div>
                }
                <span class="small">{{ c.meta }}</span>
                @if (c.canIssueTicket) {
                  <button type="button" class="btn primary sm" (click)="issue(c)">Émettre le ticket</button>
                }
                @if (hasTicket(col.stage)) {
                  <button type="button" class="btn sm" (click)="view(c)">Voir le ticket</button>
                }
                @if (c.canRemind && auth.can('TRANSITAIRE_RELANCER')) {
                  <button type="button" class="btn sm" (click)="remind(c)">Relancer le transitaire</button>
                }
              </article>
            } @empty { <span class="muted small">Aucun véhicule</span> }
          </section>
        }
      </div>
    </div>
    @if (shown(); as t) { <dat-ticket-dialog [ticket]="t" (close)="shown.set(null)" /> }
  `,
  styles: [`
    .board { display: grid; grid-template-columns: repeat(5, minmax(220px, 1fr)); gap: 16px; overflow-x: auto; }
    .column { background: #eae6dc; border-radius: 12px; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
    .head h2 { font-size: 14px; }
    .dot { width: 10px; height: 10px; border-radius: 50%; }
    .count { font-size: 12px; font-weight: 600; background: #fff; border-radius: 999px; padding: 2px 8px; }
    .hint { font-size: 12px; color: var(--text-2); }
    .kcard { background: #fff; border: 1px solid var(--line); border-radius: 10px; padding: 12px; display: flex; flex-direction: column; gap: 6px; }
    .strong { font-weight: 600; } .small { font-size: 12px; } .wrap { flex-wrap: wrap; gap: 4px; }
    .miss { font-size: 11px; font-weight: 600; background: var(--warn-bg); color: var(--warn-ink); border-radius: 4px; padding: 2px 6px; }
  `],
})
export class EnlevementsComponent implements OnInit {
  private api = inject(ApiService);
  auth = inject(AuthService);
  columns = signal<PickupColumn[]>([]);
  message = signal<string | null>(null);
  shown = signal<TicketDto | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.board().subscribe((b) => this.columns.set(b));
  }

  dot(stage: string): string {
    return DOTS[stage] ?? '#8A93A0';
  }

  issue(c: PickupCard): void {
    this.api.issueTicket(c.vin).subscribe({
      next: (t) => {
        this.message.set(`Ticket ${t.ticketNumber} émis pour ${c.shortCode} · le client est prévenu par SMS`);
        this.shown.set(t);
        this.load();
      },
      error: (e) => this.message.set(e?.error?.message ?? 'Émission impossible'),
    });
  }

  hasTicket(stage: string): boolean {
    return stage === 'TICKET_EMIS' || stage === 'AU_PORTAIL' || stage === 'SORTI';
  }

  view(c: PickupCard): void {
    this.api.passport(c.vin).subscribe({
      next: (p) => (p.ticket ? this.shown.set(p.ticket) : this.message.set(`Aucun ticket pour ${c.shortCode}`)),
      error: (e) => this.message.set(e?.error?.message ?? 'Ticket introuvable'),
    });
  }

  remind(c: PickupCard): void {
    this.api.remind(c.vin).subscribe(() => this.message.set(`Relance envoyée au transitaire de ${c.shortCode}`));
  }
}
