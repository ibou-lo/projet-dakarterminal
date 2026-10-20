import { Component, ElementRef, OnInit, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { ActionProposal, ChatReply, Passport } from '../core/models';

interface Turn { role: 'user' | 'ai'; text: string; reply?: ChatReply; }

const DEMO_VIN = 'VF3LCYHZPJS218456';

/** Chat IA superviseur : questions en langage naturel, actions validées par un humain. */
@Component({
  selector: 'dat-assistant',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="layout">
      <main class="chat">
        <div class="thread" #thread>
          @for (t of turns(); track $index) {
            @if (t.role === 'user') {
              <div class="bubble user">{{ t.text }}</div>
            } @else {
              <div class="ai">
                <div class="avatar" aria-hidden="true">✦</div>
                <div class="col grow">
                  <div class="text">{{ t.text }}</div>
                  @if (t.reply?.vehicle; as v) {
                    <div class="vcard">
                      <div class="col"><strong class="mono">{{ v.vin }}</strong><span class="muted">{{ v.model }} · {{ v.color }} · BL {{ v.blNumber }}</span>
                        <div class="row"><span [class]="'badge ' + v.status">{{ v.statusLabel }}</span><span class="pill">{{ v.formalitiesDone }} / {{ v.formalitiesTotal }} formalités</span></div></div>
                      <span class="grow"></span>
                      <div class="place mono">{{ v.placeLabel }}</div>
                    </div>
                  }
                  @if (t.reply?.table; as tb) {
                    <table class="data small-table">
                      <thead><tr>@for (h of tb.headers; track h) { <th>{{ h }}</th> }</tr></thead>
                      <tbody>@for (r of tb.rows; track $index) { <tr>@for (c of r; track $index) { <td>{{ c }}</td> }</tr> }</tbody>
                    </table>
                  }
                  @if (t.reply?.action; as a) {
                    <div class="action">
                      <span class="kicker">Action proposée</span>
                      <span>{{ a.description }}</span>
                      @if (status(a) === 'PROPOSED') {
                        <div class="row">
                          <button type="button" class="btn primary sm" (click)="decide(a, true)">Valider l'action</button>
                          <button type="button" class="btn sm" (click)="decide(a, false)">Annuler</button>
                        </div>
                      } @else if (status(a) === 'CONFIRMED') {
                        <div class="banner ok" role="status">Action validée par {{ auth.user()?.fullName }} · journalisée</div>
                      } @else {
                        <div class="banner" role="status">Action annulée</div>
                      }
                    </div>
                  }
                  @if (t.reply?.sources?.length) {
                    <div class="row src">Sources : @for (s of t.reply!.sources; track s) { <span class="srcchip">{{ s }}</span> }</div>
                  }
                </div>
              </div>
            }
          }
        </div>
        <div class="composer">
          <div class="row wrap">
            @for (s of suggestions(); track s) { <button type="button" class="chip" (click)="send(s)">{{ s }}</button> }
          </div>
          <form class="inputbar" (ngSubmit)="send(message)">
            <label class="sr-only" for="msg">Message à l'assistant</label>
            <input id="msg" name="msg" [(ngModel)]="message" placeholder="Posez une question sur un véhicule, une zone, un enlèvement…" autocomplete="off">
            <button type="submit" class="btn primary" [disabled]="busy()">Envoyer</button>
          </form>
          @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
        </div>
      </main>

      <aside class="context col">
        <section class="col">
          <h3>Véhicule en contexte</h3>
          @if (context(); as p) {
            <div class="box col"><strong class="mono">{{ p.vehicle.vin }}</strong><span class="muted">{{ p.vehicle.placeLabel }} · {{ p.vehicle.statusLabel }}</span>
              <a [routerLink]="['/parc']" [queryParams]="{ vin: p.vehicle.vin }">Ouvrir le passeport</a></div>
          }
        </section>
        <section class="col">
          <h3>Ce que l'assistant peut faire</h3>
          <ul><li>Localiser, résumer un dossier, lister des véhicules</li><li>Repérer les scans manquants et les incohérences</li><li>Proposer alertes, relances et tâches de vérification</li></ul>
        </section>
        <section class="card dark col">
          <h3>Garde-fous</h3>
          <ul class="guard"><li>Répond uniquement à partir des données enregistrées, sources citées</li><li>Accès limité au rôle (JWT)</li><li>Aucune action sans validation humaine</li><li>Chaque action est journalisée</li><li>Ne décide jamais d'une sortie : l'agent contrôle au portail</li></ul>
        </section>
      </aside>
    </div>
  `,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; height: calc(100vh - 64px); }
    .chat { display: flex; flex-direction: column; min-height: 0; }
    .thread { flex: 1; overflow-y: auto; padding: 24px 40px; display: flex; flex-direction: column; gap: 18px; }
    .bubble.user { align-self: flex-end; max-width: 560px; background: var(--ink); color: #fff; border-radius: 16px 16px 4px 16px; padding: 12px 16px; line-height: 1.5; }
    .ai { display: flex; gap: 12px; max-width: 760px; }
    .avatar { width: 32px; height: 32px; flex-shrink: 0; border-radius: 10px; background: var(--orange); color: #fff; display: flex; align-items: center; justify-content: center; }
    .text { line-height: 1.55; font-size: 15px; }
    .vcard { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px 16px; display: flex; gap: 16px; align-items: center; }
    .place { border: 2px solid var(--orange); border-radius: 8px; padding: 6px 10px; font-weight: 600; }
    .small-table { border: 1px solid var(--line); border-radius: 12px; overflow: hidden; }
    .action { background: #fff; border: 1.5px solid var(--ink); border-radius: 12px; padding: 12px 16px; display: flex; flex-direction: column; gap: 8px; }
    .kicker { font-size: 12px; font-weight: 600; letter-spacing: .6px; text-transform: uppercase; color: var(--rust); }
    .src { font-size: 12px; color: var(--muted); flex-wrap: wrap; }
    .srcchip { border: 1px solid var(--line); border-radius: 999px; padding: 2px 9px; background: #fff; }
    .composer { border-top: 1px solid var(--line); padding: 12px 40px 20px; display: flex; flex-direction: column; gap: 10px; }
    .wrap { flex-wrap: wrap; gap: 8px; }
    .inputbar { display: flex; gap: 8px; background: #fff; border: 1.5px solid var(--ink); border-radius: 14px; padding: 6px; }
    .inputbar input { flex: 1; border: 0; outline: none; font-size: 15px; padding: 0 10px; }
    .context { background: #fff; border-left: 1px solid var(--line); padding: 22px; gap: 20px; overflow-y: auto; }
    h3 { font-size: 12px; font-weight: 600; letter-spacing: .6px; text-transform: uppercase; color: var(--muted); }
    .card.dark h3 { color: #fff; }
    .box { border: 1px solid var(--line); border-radius: 12px; padding: 12px 14px; gap: 4px; }
    ul { margin: 0; padding-left: 18px; line-height: 1.7; font-size: 13px; }
    .guard { color: #d5dce4; }
  `],
})
export class AssistantComponent implements OnInit {
  private api = inject(ApiService);
  auth = inject(AuthService);
  vin = input<string | undefined>();
  thread = viewChild<ElementRef<HTMLDivElement>>('thread');

  turns = signal<Turn[]>([]);
  suggestions = signal<string[]>(['Où est le véhicule …218456 et que manque-t-il pour son enlèvement ?', 'Véhicules prêts depuis plus de 48 h ?', 'Mouvements sans scan aujourd\'hui']);
  context = signal<Passport | null>(null);
  statuses = signal<Record<number, string>>({});
  busy = signal(false);
  error = signal<string | null>(null);
  message = '';

  ngOnInit(): void {
    this.api.passport(this.vin() ?? DEMO_VIN).subscribe((p) => this.context.set(p));
  }

  send(text: string): void {
    const q = text.trim();
    if (!q) return;
    this.message = '';
    this.busy.set(true);
    this.error.set(null);
    this.turns.update((t) => [...t, { role: 'user', text: q }]);
    this.api.chat(q, this.context()?.vehicle.vin).subscribe({
      next: (r) => {
        this.turns.update((t) => [...t, { role: 'ai', text: r.text, reply: r }]);
        if (r.suggestions?.length) this.suggestions.set(r.suggestions);
        if (r.vehicle) this.api.passport(r.vehicle.vin).subscribe((p) => this.context.set(p));
        this.busy.set(false);
        setTimeout(() => this.thread()?.nativeElement.scrollTo({ top: 1e6, behavior: 'smooth' }));
      },
      error: (e) => {
        this.error.set(e?.error?.message ?? 'Réponse impossible');
        this.busy.set(false);
      },
    });
  }

  status(a: ActionProposal): string {
    return this.statuses()[a.id] ?? a.status;
  }

  decide(a: ActionProposal, accept: boolean): void {
    this.api.decideAction(a.id, accept).subscribe((r) => this.statuses.update((s) => ({ ...s, [a.id]: r.status })));
  }
}
