#!/usr/bin/env node
/**
 * Au build Vercel, écrit l'URL de l'API (Render, Railway…) dans api-base.ts.
 */
const fs = require('fs');
const path = require('path');

const url = (process.env.API_BASE_URL || process.env.RAILWAY_API_URL || '').trim().replace(/\/$/, '');
const file = path.join(__dirname, '../src/app/core/api-base.ts');
const source = `/**
 * Base de l'API. Vide = même origine (proxy local ou rewrite Vercel).
 * Remplacé au build si API_BASE_URL ou RAILWAY_API_URL est défini.
 */
export const apiBase = ${JSON.stringify(url)};
`;
fs.writeFileSync(file, source);
if (url) {
  console.log('API Railway :', url);
} else {
  console.log('API : même origine (/api via proxy ou rewrite Vercel)');
}
