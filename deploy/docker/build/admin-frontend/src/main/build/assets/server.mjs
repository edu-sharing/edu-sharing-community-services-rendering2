// Minimaler statischer Webserver für die Angular-Admin-SPA.
// Nutzt ausschließlich Node-Bordmittel (keine Runtime-Dependencies) und liefert für
// unbekannte Pfade index.html aus (SPA / Client-side Routing).
//
// Laufzeit-Konfiguration über Umgebungsvariablen (damit dasselbe Image hinter
// nginx-proxy via VIRTUAL_* ODER einer externen Apache2-Config funktioniert):
//   PORT            Listen-Port (Default 8080)
//   WEB_ROOT        Verzeichnis mit den statischen Dateien (Default /application)
//   BASE_HREF       Pfad-Präfix, unter dem die UI ausgeliefert wird (Default "/").
//                   Muss zum Routing-Pfad des Proxys passen (z.B. "/rendering-admin/").
//                   Wird beim Ausliefern in <base href> geschrieben; der Server entfernt das
//                   Präfix beim Auflösen der Dateien (der Proxy leitet den vollen Pfad weiter).
//   ADMIN_API_BASE  Basis-URL der Admin-API, in window.RS2_ADMIN_CONFIG.apiBase injiziert
//                   (typischerweise der Context-Path des Service, z.B. "/rendering").
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const ROOT = process.env.WEB_ROOT || '/application';
const PORT = Number(process.env.PORT || 8080);
const ADMIN_API_BASE = process.env.ADMIN_API_BASE || '';

// BASE_HREF auf führenden/abschließenden Slash normalisieren ("/" oder "/foo/").
function normalizeBaseHref(value) {
  if (!value || value === '/') return '/';
  let v = value.startsWith('/') ? value : `/${value}`;
  if (!v.endsWith('/')) v = `${v}/`;
  return v;
}
const BASE_HREF = normalizeBaseHref(process.env.BASE_HREF);
const BASE_PREFIX = BASE_HREF.replace(/\/$/, ''); // "" oder "/foo"

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
};

async function tryFile(path) {
  try {
    const s = await stat(path);
    if (s.isFile()) {
      return await readFile(path);
    }
  } catch {
    /* not found */
  }
  return null;
}

/** Liefert index.html mit zur Laufzeit gesetztem <base href> und Admin-Config-Script aus. */
async function renderIndex() {
  const raw = await tryFile(join(ROOT, 'index.html'));
  if (raw === null) return null;
  let html = raw.toString('utf-8');
  html = html.replace(/<base href="[^"]*">/, `<base href="${BASE_HREF}">`);
  const config = `<script>window.RS2_ADMIN_CONFIG=${JSON.stringify({ apiBase: ADMIN_API_BASE })};</script>`;
  html = html.replace('</head>', `${config}</head>`);
  return Buffer.from(html, 'utf-8');
}

const server = createServer(async (req, res) => {
  const rawPath = decodeURIComponent((req.url || '/').split('?')[0]);

  // Health-/Readiness-Endpoint (auch mit Pfad-Präfix erreichbar) – wird von den Helm-Probes
  // und Compose unter /ping abgefragt.
  if (rawPath === '/ping' || rawPath === `${BASE_PREFIX}/ping`) {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }

  // Pfad-Präfix (BASE_HREF) entfernen – der Proxy leitet den vollen Pfad weiter.
  let urlPath = rawPath;
  if (BASE_PREFIX && (urlPath === BASE_PREFIX || urlPath.startsWith(`${BASE_PREFIX}/`))) {
    urlPath = urlPath.slice(BASE_PREFIX.length) || '/';
  }

  // Pfad-Traversal verhindern
  const safePath = normalize(urlPath).replace(/^(\.\.[/\\])+/, '');

  // Konkrete Datei?
  const content = safePath === '/' ? null : await tryFile(join(ROOT, safePath));
  if (content !== null) {
    res.writeHead(200, { 'Content-Type': MIME[extname(safePath)] || 'application/octet-stream' });
    res.end(content);
    return;
  }

  // SPA-Fallback: index.html (mit Laufzeit-Config), nicht cachebar.
  const index = await renderIndex();
  if (index === null) {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not found');
    return;
  }
  res.writeHead(200, { 'Content-Type': MIME['.html'], 'Cache-Control': 'no-cache' });
  res.end(index);
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`admin-frontend static server on :${PORT} (root=${ROOT}, baseHref=${BASE_HREF}, apiBase='${ADMIN_API_BASE}')`);
});
