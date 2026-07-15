// Zentrale Konfiguration des RS2-Converter-Lasttests.
//
// Alle Werte sind per Umgebungsvariable (`-e KEY=value`) überschreibbar, damit dieselben
// Skripte lokal (Phase A) und im Cluster (Phase B) laufen.

// Öffentliche API des Service (POST /public/renderdata, GET /public/job). Lokal 8080.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// repoId muss mit "TEST" beginnen, damit bei app.security.enabled=false die synthetische
// Registrierung greift (RepositoryRegistrationStorageService).
export const REPO_ID = __ENV.REPO_ID || 'TEST_LOADTEST';

// Poll-Verhalten für GET /public/job.
export const POLL_INTERVAL_MS = Number(__ENV.POLL_INTERVAL_MS || 500);
export const POLL_TIMEOUT_MS = Number(__ENV.POLL_TIMEOUT_MS || 120000);

// Signatur-Algorithmus im Request-Body. Wird bei app.security.enabled=false nicht geprüft,
// muss aber syntaktisch gesetzt sein.
export const SIGNATURE_ALGORITHM = __ENV.SIGNATURE_ALGORITHM || 'SHA256withRSA';

// Modul-Katalog: nodeId (TEST_-Präfix -> classpath:loadtest/<datei>), mimetype (steuert den
// Dispatch im ModuleRegistry) und mediatype. Die Quelldateien liegen unter
// service/src/main/resources/loadtest/.
//
// WICHTIG: mediatype MUSS gesetzt sein. Im S3-Modus `byType` bildet der Service daraus den
// Bucket-Namen `rs2-<mediatype>` (BucketPerMediaTypeStrategy). Ein leerer mediatype ergibt den
// ungültigen Bucket-Namen `rs2-` (endet auf `-`) -> S3 lehnt jeden Request mit HTTP 400 ab,
// wodurch /public/renderdata pauschal 500 zurückgibt und kein Job entsteht. Für image/audio/
// video/document/pdf/jupyter steuert allein der mimetype den Dispatch; der mediatype dient hier
// nur der Bucket-Bildung (kein Modul ist per `type` registriert außer h5p="file-h5p").
export const MODULES = {
  image:    { nodeId: 'TEST_loadtest/image.jpg',      mimetype: 'image/jpeg',               mediatype: 'image' },
  imagePng: { nodeId: 'TEST_loadtest/image.png',      mimetype: 'image/png',                mediatype: 'image' },
  audio:    { nodeId: 'TEST_loadtest/audio.wav',      mimetype: 'audio/x-wav',              mediatype: 'audio' },
  video:    { nodeId: 'TEST_loadtest/video.mp4',      mimetype: 'video/mp4',                mediatype: 'video' },
  document: { nodeId: 'TEST_loadtest/document.txt',   mimetype: 'text/plain',               mediatype: 'document' },
  pdf:      { nodeId: 'TEST_loadtest/document.pdf',   mimetype: 'application/pdf',           mediatype: 'document' },
  jupyter:  { nodeId: 'TEST_loadtest/notebook.ipynb', mimetype: 'application/x-ipynb+json',  mediatype: 'document' },
  // h5p benötigt eine echte .h5p-Datei unter service/src/main/resources/loadtest/, den
  // laufenden lumi-Sidecar (Port 9112) und das optionale Modul H5P (loadtest-Profil). h5p
  // dispatcht per `type` -> mediatype MUSS "file-h5p" sein (Bucket rs2-file-h5p).
  // h5p:   { nodeId: 'TEST_loadtest/content.h5p',     mimetype: '',                          mediatype: 'file-h5p' },
};

export function moduleConfig(key) {
  const cfg = MODULES[key];
  if (!cfg) {
    throw new Error(`Unknown module '${key}'. Known: ${Object.keys(MODULES).join(', ')}`);
  }
  if (!cfg.mediatype) {
    // Laut scheitern statt als kryptischer S3-400: der byType-Bucket braucht einen mediatype.
    throw new Error(`Module '${key}' has an empty mediatype; byType storage would produce the invalid bucket 'rs2-'.`);
  }
  return cfg;
}
