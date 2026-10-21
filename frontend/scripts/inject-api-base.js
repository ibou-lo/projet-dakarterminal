#!/usr/bin/env node
/**
 * Au build Vercel, écrit l'URL Railway dans api-base.ts pour que le poste web
 * appelle l'API en HTTPS même si le rewrite n'est pas encore renseigné.
 */
const fs = require('fs');
const path = require('path');

const url = (process.env.RAILWAY_API_URL || '').trim().replace(/\/$/, '');
const file = path.join(__dirname, '../src/app/core/api-base.ts');
const source = `/**
 * Base de l'API. Vide = même origine (proxy local ou rewrite Vercel).
 * Remplacé au build si RAILWAY_API_URL est défini (voir scripts/inject-api-base.js).
 */
export const apiBase = ${JSON.stringify(url)};
`;
fs.writeFileSync(file, source);
if (url) {
  console.log('API Railway :', url);
} else {
  console.log('API : même origine (/api via proxy ou rewrite Vercel)');
}
