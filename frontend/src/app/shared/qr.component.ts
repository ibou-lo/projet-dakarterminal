import { Component, effect, input, signal } from '@angular/core';
import QRCode from 'qrcode';

/** QR code rendu en image (bibliothèque « qrcode »). */
@Component({
  selector: 'dat-qr',
  standalone: true,
  template: `@if (src(); as s) { <img [src]="s" [width]="size()" [height]="size()" [alt]="'QR ' + data()"> }`,
  styles: [`img { display: block; image-rendering: pixelated; }`],
})
export class QrComponent {
  data = input.required<string>();
  size = input(96);
  src = signal<string | null>(null);

  constructor() {
    effect(() => {
      const d = this.data();
      QRCode.toDataURL(d, { width: this.size() * 3, margin: 1, color: { dark: '#0F1D2B', light: '#FFFFFF' } })
        .then((url: string) => this.src.set(url))
        .catch(() => this.src.set(null));
    }, { allowSignalWrites: true });
  }
}
