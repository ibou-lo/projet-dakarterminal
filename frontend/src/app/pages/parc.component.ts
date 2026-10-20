import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { ChatReply, ParkOverview, Passport, TicketDto } from '../core/models';
import { PassportPanelComponent } from '../shared/passport-panel.component';
import { ZoneGridComponent } from '../shared/zone-grid.component';
import { ZonesDialogComponent } from '../shared/zones-dialog.component';
import { AuthService } from '../core/auth.service';

const DEMO_VIN = 'VF3LCYHZPJS218456';

/** Poste de supervision : plan du parc, passeport numérique, assistant de recherche. */
@Component({
  selector: 'dat-parc',
  standalone: true,
  imports: [ZoneGridComponent, ZonesDialogComponent, PassportPanelComponent, FormsModule, RouterLink, DatePipe],
  template: `
    <div class="layout">
      <div class="main">
        @if (overview(); as o) {
          <div class="tiles">
            <div class="tile"><span class="label">Véhicules sur le parc</span><span class="value">{{ o.vehiclesOnPark }}</span></div>
            <div class="tile"><span class="label">Positions confirmées par scan</span><span class="value">{{ o.confirmedPositionsPct }} %</span></div>
            <div class="tile"><span class="label">Prêts à enlever</span><span class="value ok">{{ o.readyToPickUp }}</span></div>
            <div class="tile"><span class="label">Mouvements tracés · 24 h</span><span class="value">{{ o.movements24h }}</span></div>
          </div>

          <section class="card col">
            <div class="row">
              <h2>Plan du parc véhicules</h2>
              <span class="pill">Données fictives de démonstration</span>
              <span class="grow"></span>
              <span class="legend"><i class="lg occ"></i>Occupée <i class="lg ready"></i>Prêt à enlever <i class="lg free"></i>Libre <i class="lg sel"></i>Sélection</span>
              @if (isSupervisor()) {
                <button type="button" class="btn sm primary" (click)="zonesOpen.set(true)">+ Ajouter une zone</button>
              }
            </div>
            <div class="zones">
              @for (z of o.zones; track z.zone) {
                <dat-zone-grid [zone]="z" [selectedVin]="selectedVin()" [focus]="z.zone === 'B'" (pick)="select($event)" />
              }
            </div>
          </section>
        }

        <section class="card dark col assistant">
          <div class="row">
            <h2>Assistant de recherche</h2>
            <span class="muted-ink">Répond uniquement à partir des données enregistrées</span>
            <span class="grow"></span>
            <a class="btn sm ghost" routerLink="/assistant">Ouvrir le chat IA ↗</a>
          </div>
          <form class="row" (ngSubmit)="ask()">
            <label class="sr-only" for="q">Question à l'assistant</label>
            <input id="q" name="q" class="field grow" [(ngModel)]="question" placeholder="Où est le véhicule …218456 et que manque-t-il ?">
            <button class="btn" type="submit" style="background: var(--peach); border-color: var(--peach)">Demander</button>
          </form>
          @if (reply(); as r) {
            <div class="answer">{{ r.text }}</div>
            <div class="row src">Sources :
              @for (s of r.sources; track s) { <span class="srcchip">{{ s }}</span> }
            </div>
          }
        </section>
      </div>

      <div class="side">
        @if (zonesOpen()) {
          <dat-zones-dialog (close)="zonesOpen.set(false)" (changed)="refresh()" />
        }
        <dat-passport-panel [passport]="passport()" (changed)="onChanged($event)" (ticketIssued)="ticket.set($event)" />
      </div>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 440px; gap: 24px; padding: 24px; align-items: start; }
    .main, .side { display: flex; flex-direction: column; gap: 16px; }
    .value.ok { color: var(--ok); }
    .zones { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
    .legend { flex-wrap: wrap; font-size: 12px; color: var(--text-2); display: flex; align-items: center; gap: 6px; }
    .lg { display: inline-block; width: 11px; height: 15px; border-radius: 3px; margin-left: 8px; }
    .lg.occ { background: var(--slot-occupied); } .lg.ready { background: var(--slot-ready); } .lg.free { border: 1px dashed #a49d8e; } .lg.sel { background: var(--orange); }
    .assistant input.field { background: var(--ink-2); border-color: var(--ink-line); color: #fff; }
    .muted-ink { color: var(--on-ink-muted); font-size: 12px; }
    .ghost { background: transparent; color: #fff; border-color: var(--ink-line); }
    .answer { background: var(--ink-2); border-radius: 10px; padding: 12px 16px; line-height: 1.55; }
    .src { font-size: 12px; color: var(--on-ink-muted); flex-wrap: wrap; }
    .srcchip { border: 1px solid var(--ink-line); border-radius: 999px; padding: 2px 10px; }
    .kicker { font-size: 12px; font-weight: 600; letter-spacing: .8px; text-transform: uppercase; color: var(--muted); }
    .num { font-family: var(--display); font-size: 24px; }
    .qr { font-family: var(--mono); font-size: 11px; word-break: break-all; background: var(--ground); padding: 8px; border-radius: 6px; }
    .small { font-size: 12px; }
    @media (max-width: 1200px) { .layout { grid-template-columns: 1fr; } .zones { grid-template-columns: 1fr; } }
  `],
})
export class ParcComponent implements OnInit {
  private api = inject(ApiService);
  private auth = inject(AuthService);
  isSupervisor = computed(() => this.auth.can('ZONE_GERER'));
  zonesOpen = signal(false);
  overview = signal<ParkOverview | null>(null);
  passport = signal<Passport | null>(null);
  reply = signal<ChatReply | null>(null);
  ticket = signal<TicketDto | null>(null);
  selectedVin = computed(() => this.passport()?.vehicle.vin ?? null);
  /** Paramètre d'URL ?vin=… (liaison automatique des entrées du routeur). */
  vin = input<string | undefined>();
  question = 'Où est le véhicule …218456 et que manque-t-il pour son enlèvement ?';

  ngOnInit(): void {
    this.refresh();
    this.select(this.vin() ?? DEMO_VIN);
  }

  refresh(): void {
    this.api.park().subscribe((o) => this.overview.set(o));
  }

  select(vin: string): void {
    this.ticket.set(null);
    this.api.passport(vin).subscribe((p) => this.passport.set(p));
  }

  onChanged(p: Passport): void {
    this.passport.set(p);
    this.refresh();
    if (this.reply()) this.ask();
  }

  ask(): void {
    if (!this.question.trim()) return;
    this.api.chat(this.question, this.selectedVin()).subscribe((r) => this.reply.set(r));
  }
}
