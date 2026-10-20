import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

/** Mon compte : informations et changement de mot de passe (tous les profils du personnel). */
@Component({
  selector: 'dat-compte',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="page narrow">
      <div class="page-head"><div class="grow"><h1>Mon compte</h1><div class="sub">Informations et mot de passe</div></div></div>

      <section class="card info">
        <div class="av">{{ initials() }}</div>
        <div>
          <strong>{{ auth.user()?.fullName }}</strong>
          <div class="muted">{{ auth.roleLabel() }}@if (auth.user()?.zone) { · zone {{ auth.user()?.zone }} }</div>
          <div class="mono muted">{{ auth.user()?.username }}</div>
        </div>
      </section>

      @if (auth.user()?.mustChangePassword) {
        <div class="banner warn" role="alert">Vous utilisez un mot de passe provisoire : choisissez votre mot de passe personnel pour continuer.</div>
      }
      <form class="card col" (ngSubmit)="submit()" #f="ngForm">
        <h2>Changer mon mot de passe</h2>
        <label>Mot de passe actuel
          <input class="field" type="password" name="cur" [(ngModel)]="current" required autocomplete="current-password">
        </label>
        <label>Nouveau mot de passe
          <input class="field" type="password" name="new" [(ngModel)]="next" required minlength="12" maxlength="128" autocomplete="new-password">
          <span class="help" [class.ok]="strong()">12 caractères minimum, avec lettres et chiffres, sans votre identifiant. Une courte phrase est idéale.</span>
        </label>
        <label>Confirmer le nouveau mot de passe
          <input class="field" type="password" name="conf" [(ngModel)]="confirm" required autocomplete="new-password">
          @if (confirm && confirm !== next) { <span class="help bad">Les deux saisies sont différentes</span> }
        </label>
        @if (message(); as m) { <div class="banner" [class.ok]="m.ok" [class.danger]="!m.ok" role="status">{{ m.text }}</div> }
        <div class="row">
          <span class="grow"></span>
          <button class="btn primary" type="submit" [disabled]="!f.valid || !strong() || confirm !== next || busy()">Enregistrer</button>
        </div>
      </form>
    </div>
  `,
  styles: [`
    .narrow { max-width: 640px; }
    .info { display: flex; gap: 14px; align-items: center; }
    .av { width: 52px; height: 52px; border-radius: 50%; background: var(--ink); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; }
    .muted { color: var(--muted); }
    .mono { font-family: var(--mono); font-size: 12px; }
    label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; color: var(--text-2); }
    .help { font-size: 12px; font-weight: 400; color: var(--muted); }
    .help.ok { color: var(--ok); }
    .help.bad { color: var(--danger); }
  `],
})
export class CompteComponent {
  private api = inject(ApiService);
  auth = inject(AuthService);
  current = '';
  next = '';
  confirm = '';
  busy = signal(false);
  message = signal<{ ok: boolean; text: string } | null>(null);

  strong(): boolean {
    return this.next.length >= 12 && /[A-Za-z]/.test(this.next) && /\d/.test(this.next);
  }

  initials(): string {
    return (this.auth.user()?.fullName || '?').split(/[\s.]+/).filter(Boolean).map((p) => p[0]).join('').slice(0, 2).toUpperCase();
  }

  submit(): void {
    this.busy.set(true);
    this.api.changePassword(this.current, this.next).subscribe({
      next: (t) => {
        this.busy.set(false);
        this.current = this.next = this.confirm = '';
        this.auth.adopt(t);
        this.auth.notice.set(null);
        this.message.set({ ok: true, text: 'Mot de passe changé. Vos autres sessions ont été fermées.' });
      },
      error: (e) => {
        this.busy.set(false);
        this.message.set({ ok: false, text: e?.error?.message || 'Changement impossible' });
      },
    });
  }
}
