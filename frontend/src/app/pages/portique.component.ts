import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ZoneStore } from '../core/zone.store';
import { GatePassageDto, GateResult } from '../core/models';

/**
 * Portail de sortie : lecture RFID (simulée par la saisie d'une étiquette ou d'un VIN)
 * et/ou scan du QR du ticket. La barrière ne s'ouvre jamais seule.
 */
@Component({
  selector: 'dat-portique',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><h1>Portail de sortie · lecture automatique</h1>
          <div class="sub">Le portique lit l'étiquette RFID ; l'application croise l'identité avec l'autorisation et le ticket</div></div>
        <span class="pill">Données fictives de démonstration</span>
      </div>

      <div class="layout">
        <div class="col" style="gap: 16px">
          <form class="card col" (ngSubmit)="scan()">
            <h2>Simuler un passage</h2>
            <div class="row wrap">
              <label class="col grow">Étiquette RFID (EPC) ou VIN
                <input class="field" name="tag" [(ngModel)]="tag" placeholder="E280-JS218456 ou VF3LCYHZPJS218456">
              </label>
              <label class="col grow">QR du ticket (optionnel)
                <input class="field" name="qr" [(ngModel)]="qr" placeholder="TKT.TR-2026-…">
              </label>
            </div>
            <div class="row wrap">
              <span class="muted small">Exemples :</span>
              <button type="button" class="chip" (click)="preset('WDB9066331S772018')">Véhicule autorisé (au portail)</button>
              <button type="button" class="chip" (click)="preset('ZFA31200000904311')">Véhicule non autorisé</button>
              <button type="button" class="chip" (click)="preset('VF3LCYHZPJS218456')">Véhicule de la démo</button>
            </div>
            <button type="submit" class="btn dark" [disabled]="busy()">Lire au portique</button>
            @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
          </form>

          @if (result(); as r) {
            <section class="card result" style="padding: 0; overflow: hidden">
              <div class="banner" [class.ok]="r.decision !== 'BLOCKED'" [class.danger]="r.decision === 'BLOCKED'" role="status" style="border-radius: 0; padding: 16px 20px">
                <div class="col grow" style="gap: 2px"><strong class="title">{{ r.title }}</strong><span>{{ r.subtitle }}</span></div>
                <strong class="mono">{{ r.vin }}</strong>
              </div>
              <div class="checks">
                @for (c of r.checks; track c.code) {
                  <div class="check">
                    <span class="ico" [attr.data-r]="c.result" aria-hidden="true">{{ icon(c.result) }}</span>
                    <div class="col" style="gap: 2px"><strong>{{ c.label }}</strong><span class="muted">{{ c.note }}</span></div>
                  </div>
                }
              </div>
              <div class="row foot">
                <span class="muted grow">La barrière ne s'ouvre jamais seule : l'agent valide après ses contrôles.</span>
                @if (r.decision === 'PENDING_AGENT' && auth.can('SORTIE_CONTROLER')) {
                  <button type="button" class="btn" (click)="decide(r, 'BLOCKED')">Signaler / bloquer</button>
                  <button type="button" class="btn success" (click)="decide(r, 'EXIT_VALIDATED')">Ouvrir la barrière</button>
                } @else if (r.decision === 'PENDING_AGENT') {
                  <span class="muted">Décision réservée aux profils autorisés à contrôler les sorties</span>
                } @else if (r.decision === 'BLOCKED') {
                  <button type="button" class="btn danger solid">Garder fermé · superviseur alerté</button>
                } @else {
                  <span class="banner ok">Sortie validée</span>
                }
              </div>
            </section>
          }
        </div>

        <aside class="col" style="gap: 16px">
          <form class="card col" (ngSubmit)="zoneRead()">
            <h2>Portiques d'entrée de zone</h2>
            <span class="muted small">Simule la lecture RFID d'un véhicule entrant dans une zone. Une zone différente de sa fiche crée une incohérence (voir Fraîcheur).</span>
            <div class="row">
              <select class="field" name="zz" [(ngModel)]="zoneGate" aria-label="Zone du portique">
                @for (z of zoneStore.codes(); track z) { <option [value]="z">Portique zone {{ z }}</option> }
              </select>
              <input class="field grow mono" name="zepc" [(ngModel)]="zoneEpc" placeholder="EPC ou VIN" aria-label="Étiquette lue">
            </div>
            <button type="submit" class="btn" [disabled]="!zoneEpc.trim()">Enregistrer la lecture</button>
            @if (zoneMsg()) { <span class="small" role="status">{{ zoneMsg() }}</span> }
          </form>
          <div class="tiles">
            <div class="tile"><span class="label">Sorties (24 h)</span><span class="value">{{ stats()['exits'] || 0 }}</span></div>
            <div class="tile"><span class="label">Tentatives bloquées</span><span class="value danger">{{ stats()['blocked'] || 0 }}</span></div>
          </div>
          <section class="card col">
            <h2>Lectures du portique</h2>
            @for (p of passages(); track p.id) {
              <div class="log">
                <span class="mono muted">{{ p.readAt | date: 'HH:mm' }}</span>
                <span class="dot" [attr.data-d]="p.decision"></span>
                <div class="col" style="gap: 0"><strong>Voie {{ p.lane }} · …{{ p.vin.slice(9) }}</strong><span class="muted">{{ decisionLabel(p) }}</span></div>
              </div>
            }
          </section>
        </aside>
      </div>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 400px; gap: 16px; align-items: start; }
    .wrap { flex-wrap: wrap; }
    label { font-weight: 600; font-size: 13px; }
    .small { font-size: 12px; }
    .title { font-family: var(--display); font-size: 20px; }
    .checks { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; padding: 6px 20px; }
    .check { display: flex; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--ground-2); }
    .ico { width: 22px; height: 22px; border-radius: 50%; color: #fff; font-weight: 700; display: flex; align-items: center; justify-content: center; flex-shrink: 0; background: var(--muted); }
    .ico[data-r='OK'] { background: var(--ok); } .ico[data-r='KO'] { background: var(--danger); }
    .foot { padding: 14px 20px; border-top: 1px solid var(--line); }
    .value.danger { color: var(--danger); }
    .log { display: flex; gap: 10px; align-items: flex-start; padding: 8px 0; border-top: 1px solid var(--ground-2); font-size: 13px; }
    .dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 5px; background: var(--muted); flex-shrink: 0; }
    .dot[data-d='EXIT_VALIDATED'] { background: var(--ok); } .dot[data-d='BLOCKED'] { background: var(--danger); } .dot[data-d='PENDING_AGENT'] { background: var(--orange); }
    @media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } }
  `],
})
export class PortiqueComponent implements OnInit {
  private api = inject(ApiService);
  auth = inject(AuthService);
  zoneStore = inject(ZoneStore);
  result = signal<GateResult | null>(null);
  passages = signal<GatePassageDto[]>([]);
  stats = signal<Record<string, number>>({});
  busy = signal(false);
  error = signal<string | null>(null);
  tag = '';
  zoneGate = 'D';
  zoneEpc = '';
  zoneMsg = signal<string | null>(null);
  qr = '';

  ngOnInit(): void {
    this.zoneStore.load();
    this.reload();
  }

  reload(): void {
    this.api.gatePassages().subscribe((p) => this.passages.set(p));
    this.api.gateStats().subscribe((s) => this.stats.set(s));
  }

  zoneRead(): void {
    const epc = this.zoneEpc.trim().toUpperCase();
    this.api.zoneRead(epc, this.zoneGate).subscribe({
      next: () => this.zoneMsg.set(`Lecture enregistrée au portique de la zone ${this.zoneGate} · chaîne de garde mise à jour`),
      error: (e) => this.zoneMsg.set(e?.error?.message ?? 'Étiquette inconnue'),
    });
  }

  preset(vin: string): void {
    this.tag = vin;
    this.qr = '';
    this.scan();
  }

  scan(): void {
    const t = this.tag.trim();
    const body: { ticketQr?: string; rfidEpc?: string; vin?: string } = {};
    if (t.startsWith('E280-')) body.rfidEpc = t;
    else if (t) body.vin = t.toUpperCase();
    if (this.qr.trim()) body.ticketQr = this.qr.trim();
    this.busy.set(true);
    this.error.set(null);
    this.api.gateScan(body).subscribe({
      next: (r) => {
        this.result.set(r);
        this.busy.set(false);
        this.reload();
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'Lecture impossible');
        this.busy.set(false);
      },
    });
  }

  decide(r: GateResult, d: 'EXIT_VALIDATED' | 'BLOCKED'): void {
    this.api.gateDecision(r.passageId, d, d === 'BLOCKED' ? 'Bloqué par l\'agent' : undefined).subscribe(() => {
      this.result.set({ ...r, decision: d, title: d === 'EXIT_VALIDATED' ? 'Sortie validée' : 'Sortie bloquée par l\'agent' });
      this.reload();
    });
  }

  icon(r: string): string {
    return r === 'OK' ? '✓' : r === 'KO' ? '!' : r === 'MANUAL' ? '?' : 'i';
  }

  decisionLabel(p: GatePassageDto): string {
    if (p.decision === 'EXIT_VALIDATED') return `Sortie validée · ${p.decidedBy ?? ''}`;
    if (p.decision === 'BLOCKED') return `Sortie bloquée · ${p.reason ?? ''}`;
    return 'Autorisé · en attente de l\'agent';
  }
}
