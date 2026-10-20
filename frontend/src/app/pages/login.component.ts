import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { homeFor } from '../core/permissions';

interface DemoAccount { user: string; pwd: string; role: string; hint: string; icon: 'sup' | 'agent' | 'gate' | 'admin'; }

/** Connexion du personnel : présentation du produit à gauche, formulaire à droite. */
@Component({
  selector: 'dat-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="page">
      <!-- Présentation -->
      <aside class="hero">
        <div class="brand">
          <svg width="44" height="44" viewBox="0 0 32 32" fill="none" aria-hidden="true"><rect x="1" y="1" width="30" height="30" rx="7" fill="#D9601A"/><path d="M8 19.5l2-5.2c.3-.8 1-1.3 1.9-1.3h8.2c.9 0 1.6.5 1.9 1.3l2 5.2M7.5 19.5h17v3.5h-17z" stroke="#fff" stroke-width="1.8" stroke-linejoin="round"/><circle cx="11" cy="23" r="1.6" fill="#fff"/><circle cx="21" cy="23" r="1.6" fill="#fff"/></svg>
          <span>Dakar AutoTrack</span>
        </div>

        <div class="pitch">
          <h1>Le passeport numérique des véhicules au terminal.</h1>
          <p>Retrouver chaque véhicule, connaître son état et vérifier s'il peut sortir, depuis une seule interface.</p>
        </div>

        <!-- Plan du parc stylisé : une place localisée par scan -->
        <svg class="plan" viewBox="0 0 440 150" role="img" aria-label="Illustration : plan d'une zone du parc, un véhicule localisé">
          @for (row of rows; track row) {
            @for (col of cols; track col) {
              <rect [attr.x]="14 + col * 26" [attr.y]="14 + row * 34" width="18" height="26" rx="3"
                    [attr.class]="slotClass(row, col)" />
            }
          }
          <line class="scan" x1="0" x2="440" y1="0" y2="0" />
          <g transform="translate(276 50)">
            <rect x="0" y="0" width="118" height="24" rx="6" class="tag" />
            <text x="59" y="16" text-anchor="middle" class="tagtxt">B · 3 · 18</text>
          </g>
        </svg>

        <ul class="points">
          <li><span class="dot">1</span><div><strong>Retrouver</strong> la place exacte, confirmée par scan QR ou RFID</div></li>
          <li><span class="dot">2</span><div><strong>Vérifier</strong> l'état et les formalités (BAE, paiement, bénéficiaire)</div></li>
          <li><span class="dot">3</span><div><strong>Sortir</strong> avec un ticket signé, contrôlé au portail</div></li>
        </ul>

        <p class="foot">Prototype · données fictives de démonstration</p>
      </aside>

      <!-- Formulaire -->
      <main class="side">
        <section class="card" aria-labelledby="login-title">
          <div class="mini-brand">
            <svg width="32" height="32" viewBox="0 0 32 32" fill="none" aria-hidden="true"><rect x="1" y="1" width="30" height="30" rx="7" fill="#D9601A"/><path d="M8 19.5l2-5.2c.3-.8 1-1.3 1.9-1.3h8.2c.9 0 1.6.5 1.9 1.3l2 5.2M7.5 19.5h17v3.5h-17z" stroke="#fff" stroke-width="1.8" stroke-linejoin="round"/></svg>
            <span>Dakar AutoTrack</span>
          </div>
          <span class="kicker">Poste de supervision</span>
          <h2 id="login-title">Connexion</h2>
          <p class="sub">Accès réservé au personnel du terminal.</p>

          <form (ngSubmit)="submit()" class="form" #f="ngForm">
            <label for="u">Identifiant</label>
            <div class="input">
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/></svg>
              <input id="u" name="username" [(ngModel)]="username" autocomplete="username" required placeholder="ex. superviseur"
                     (input)="error.set(null)">
            </div>

            <label for="p">Mot de passe</label>
            <div class="input">
              <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>
              <input id="p" name="password" [type]="showPwd() ? 'text' : 'password'" [(ngModel)]="password"
                     autocomplete="current-password" required placeholder="••••••••" (input)="error.set(null)">
              <button type="button" class="eye" (click)="showPwd.set(!showPwd())"
                      [attr.aria-label]="showPwd() ? 'Masquer le mot de passe' : 'Afficher le mot de passe'">
                {{ showPwd() ? 'Masquer' : 'Afficher' }}
              </button>
            </div>

            @if (error()) {
              <div class="error" role="alert">
                <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v6M12 16.5v.5"/></svg>
                {{ error() }}
              </div>
            }

            <button class="submit" type="submit" [disabled]="loading() || !f.valid">
              @if (loading()) { <span class="spin" aria-hidden="true"></span> Connexion… } @else { Se connecter }
            </button>
          </form>

          <div class="demo">
            <div class="demo-head">
              <span>Comptes de démonstration</span>
              <span class="muted">un clic pour remplir</span>
            </div>
            <div class="accounts">
              @for (a of accounts; track a.user) {
                <button type="button" class="acc" [class.on]="username === a.user" (click)="fill(a)">
                  <span class="ic" [attr.data-kind]="a.icon">
                    @switch (a.icon) {
                      @case ('sup') { <svg viewBox="0 0 24 24"><path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/></svg> }
                      @case ('agent') { <svg viewBox="0 0 24 24"><path d="M4 7V4h3M17 4h3v3M20 17v3h-3M7 20H4v-3M7 12h10"/></svg> }
                      @case ('admin') { <svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="3.5"/><path d="M5 20c0-3.5 3-5.5 7-5.5s7 2 7 5.5M18 4l2 2-2 2"/></svg> }
                      @default { <svg viewBox="0 0 24 24"><path d="M3 21V9l9-5 9 5v12M8 21v-7h8v7"/></svg> }
                    }
                  </span>
                  <span class="acc-t"><strong>{{ a.role }}</strong><span>{{ a.hint }}</span></span>
                </button>
              }
            </div>
            <p class="note">Les clients suivent leur véhicule dans l'application mobile (BL + code SMS).</p>
          </div>

          <p class="secure">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"/><path d="M9 12l2 2 4-4"/></svg>
            Connexion sécurisée · session de 60 min, renouvelée automatiquement
          </p>
        </section>
      </main>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .page { min-height: 100vh; display: grid; grid-template-columns: minmax(420px, 1.05fr) 1fr; background: var(--ground); }

    /* ---------- Présentation ---------- */
    .hero { background: var(--ink); color: #fff; padding: 40px 56px; display: flex; flex-direction: column; gap: 32px; position: relative; overflow: hidden; }
    .hero::after { content: ''; position: absolute; right: -120px; bottom: -120px; width: 360px; height: 360px; border-radius: 50%;
                   background: radial-gradient(circle, rgba(217, 96, 26, .22), transparent 70%); pointer-events: none; }
    .brand { display: flex; align-items: center; gap: 12px; font-family: var(--display); font-weight: 800; font-size: 20px; }
    .pitch { margin-top: auto; max-width: 520px; }
    .pitch h1 { font-family: var(--display); font-size: 38px; line-height: 1.12; font-weight: 800; margin: 0 0 14px; letter-spacing: -.3px; }
    .pitch p { color: var(--on-ink-muted); font-size: 16px; line-height: 1.55; margin: 0; }
    .plan { width: 100%; max-width: 520px; background: var(--ink-2); border: 1px solid var(--ink-line); border-radius: 14px; }
    .plan .free { fill: none; stroke: #4a627c; stroke-dasharray: 3 2; }
    .plan .occ { fill: #3a4e64; }
    .plan .ready { fill: #2e8b5e; }
    .plan .sel { fill: var(--orange); stroke: #fff; stroke-width: 1.5; }
    .plan .scan { stroke: var(--peach); stroke-width: 2; opacity: .8; animation: scan 3.2s ease-in-out infinite; }
    .plan .tag { fill: #fff; }
    .plan .tagtxt { font-family: var(--mono); font-size: 12px; font-weight: 600; fill: var(--ink); }
    @keyframes scan { 0%, 100% { transform: translateY(12px); } 50% { transform: translateY(138px); } }
    @media (prefers-reduced-motion: reduce) { .plan .scan { animation: none; transform: translateY(80px); } }
    .points { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 14px; max-width: 520px; }
    .points li { display: flex; gap: 12px; align-items: flex-start; color: var(--on-ink-muted); line-height: 1.45; }
    .points strong { color: #fff; }
    .dot { flex: none; width: 26px; height: 26px; border-radius: 50%; border: 1px solid var(--ink-line); display: flex; align-items: center; justify-content: center;
           font-size: 12px; font-weight: 700; color: var(--peach); }
    .foot { margin: 0; font-size: 12px; color: #7f8ea0; }

    /* ---------- Formulaire ---------- */
    .side { display: flex; align-items: center; justify-content: center; padding: 40px 24px; }
    .card { width: 440px; max-width: 100%; background: #fff; border: 1px solid var(--line); border-radius: 20px; padding: 36px;
            box-shadow: 0 24px 48px -24px rgba(15, 29, 43, .25); display: flex; flex-direction: column; }
    .mini-brand { display: none; align-items: center; gap: 10px; font-family: var(--display); font-weight: 800; font-size: 18px; margin-bottom: 22px; }
    .kicker { font-size: 12px; font-weight: 700; letter-spacing: .9px; text-transform: uppercase; color: var(--rust); }
    h2 { font-family: var(--display); font-size: 30px; font-weight: 800; margin: 4px 0 4px; }
    .sub { margin: 0 0 24px; color: var(--muted); }
    .form { display: flex; flex-direction: column; }
    label { font-size: 13px; font-weight: 600; color: var(--text-2); margin-bottom: 6px; }
    .input { display: flex; align-items: center; gap: 10px; height: 48px; border: 1.5px solid var(--line-2); border-radius: 10px; padding: 0 12px;
             margin-bottom: 16px; background: #fff; transition: border-color .15s, box-shadow .15s; }
    .input:focus-within { border-color: var(--ink); box-shadow: 0 0 0 3px rgba(15, 29, 43, .12); }
    .input svg { width: 18px; height: 18px; flex: none; fill: none; stroke: var(--muted); stroke-width: 1.8; stroke-linecap: round; }
    .input input { flex: 1; border: 0; outline: none; height: 100%; font: inherit; font-size: 15px; color: var(--text); background: transparent; min-width: 0; }
    .eye { border: 0; background: none; color: var(--rust); font-weight: 600; font-size: 13px; cursor: pointer; padding: 6px; border-radius: 6px; }
    .eye:hover { background: var(--ground); }
    .error { display: flex; gap: 8px; align-items: center; background: var(--danger-bg); color: var(--danger); border-radius: 10px; padding: 10px 12px;
             font-size: 14px; margin: -4px 0 14px; }
    .error svg { width: 18px; height: 18px; flex: none; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; }
    .submit { height: 50px; border: 0; border-radius: 10px; background: var(--rust); color: #fff; font: inherit; font-size: 16px; font-weight: 600;
              cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 10px; transition: background .15s; }
    .submit:hover:not(:disabled) { background: var(--rust-dark); }
    .submit:disabled { opacity: .6; cursor: not-allowed; }
    .spin { width: 16px; height: 16px; border: 2px solid rgba(255,255,255,.4); border-top-color: #fff; border-radius: 50%; animation: spin .8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }

    .demo { margin-top: 26px; padding-top: 20px; border-top: 1px solid var(--line); }
    .demo-head { display: flex; justify-content: space-between; align-items: baseline; font-size: 13px; font-weight: 600; margin-bottom: 10px; }
    .muted { color: var(--muted); font-weight: 400; font-size: 12px; }
    .accounts { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    .acc { display: flex; align-items: center; gap: 12px; text-align: left; background: #fff; border: 1px solid var(--line); border-radius: 12px;
           padding: 10px 12px; cursor: pointer; font: inherit; transition: border-color .15s, background .15s; }
    .acc:hover { border-color: var(--line-2); background: #fbfaf6; }
    .acc.on { border-color: var(--rust); background: #fdf3ec; }
    .ic { width: 36px; height: 36px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex: none; background: var(--ground); }
    .ic[data-kind='sup'] { background: var(--ink); } .ic[data-kind='sup'] svg { stroke: #fff; }
    .ic[data-kind='admin'] { background: #fbe0cc; } .ic[data-kind='admin'] svg { stroke: var(--rust); }
    .ic svg { width: 18px; height: 18px; fill: none; stroke: var(--ink); stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }
    .acc-t { display: flex; flex-direction: column; line-height: 1.3; }
    .acc-t span { font-family: var(--mono); font-size: 12px; color: var(--muted); }
    .note { font-size: 12px; color: var(--muted); margin: 12px 0 0; }
    .secure { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--muted); margin: 20px 0 0; }
    .secure svg { width: 16px; height: 16px; fill: none; stroke: var(--ok); stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; flex: none; }

    @media (max-width: 960px) {
      .page { grid-template-columns: 1fr; }
      .hero { display: none; }
      .mini-brand { display: flex; }
      .card { padding: 28px 22px; }
    }
  `],
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  username = '';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);
  showPwd = signal(false);

  readonly accounts: DemoAccount[] = [
    { user: 'superviseur', pwd: 'Superviseur@2026', role: 'Superviseur de parc', hint: 'superviseur', icon: 'sup' },
    { user: 'agent.diallo', pwd: 'Agent@2026', role: 'Agent de parc · zone B', hint: 'agent.diallo', icon: 'agent' },
    { user: 'portail.sarr', pwd: 'Portail@2026', role: 'Agent de portail', hint: 'portail.sarr', icon: 'gate' },
    { user: 'admin', pwd: 'Admin@2026', role: 'Administrateur', hint: 'admin', icon: 'admin' },
  ];

  /** Plan décoratif : 4 allées × 16 places. */
  readonly rows = [0, 1, 2, 3];
  readonly cols = Array.from({ length: 16 }, (_, i) => i);

  slotClass(row: number, col: number): string {
    if (row === 2 && col === 12) return 'sel';
    const n = (row * 7 + col * 5) % 11;
    return n < 4 ? 'occ' : n < 6 ? 'ready' : 'free';
  }

  fill(a: DemoAccount): void {
    this.username = a.user;
    this.password = a.pwd;
    this.error.set(null);
  }

  submit(): void {
    if (!this.username.trim() || !this.password) return;
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: (user) => this.router.navigateByUrl(user.mustChangePassword ? '/compte' : homeFor(user)),
      error: (e) => {
        this.error.set(e?.status === 0 ? 'Serveur injoignable : vérifiez que l\'API est démarrée (port 8080).' : (e?.error?.message ?? 'Connexion impossible'));
        this.loading.set(false);
      },
    });
  }
}
