import { Injectable, computed, inject, signal } from '@angular/core';
import { ApiService } from './api.service';
import { ZoneInfo } from './models';

/** Liste des zones du parc, partagée par toutes les pages (listes déroulantes, plan). */
@Injectable({ providedIn: 'root' })
export class ZoneStore {
  private api = inject(ApiService);
  readonly zones = signal<ZoneInfo[]>([]);
  readonly codes = computed(() => {
    const c = this.zones().map((z) => z.code);
    return c.length ? c : ['A', 'B', 'C', 'D'];
  });
  private loaded = false;

  /** Charge une fois (force = recharger après une création / suppression). */
  load(force = false): void {
    if (this.loaded && !force) return;
    this.loaded = true;
    this.api.zones().subscribe({ next: (z) => this.zones.set(z), error: () => (this.loaded = false) });
  }

  label(code: string): string {
    return this.zones().find((z) => z.code === code)?.label || '';
  }
}
