// Baut den signierten-losen /public/renderdata-Request und pollt /public/job bis zum
// terminalen Status. Exponiert die Kern-Funktion submitAndPoll(moduleKey) für die Szenarien.

import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';
import { Trend, Rate } from 'k6/metrics';
import {
  baseUrl,
  REPO_ID,
  POLL_INTERVAL_MS,
  POLL_TIMEOUT_MS,
  SIGNATURE_ALGORITHM,
  moduleConfig,
} from './config.js';

// Getaggte Metriken (Tag `module`) — pro Converter auswertbar.
const timeToFinish = new Trend('rs2_time_to_finish', true);
const jobErrors = new Rate('rs2_job_errors');
const submitErrors = new Rate('rs2_submit_errors');
// Video with several resolutions: when is the first one, and when is each one available (tag `quality`)?
const videoFirstQuality = new Trend('rs2_video_first_quality', true);
const videoQualityReady = new Trend('rs2_video_quality_ready', true);

const TERMINAL = ['FINISHED', 'FAILED', 'PARTIALLY_FAILED'];

// Node-JSON, wie es der RenderController deserialisiert. `content.hash` wird pro Aufruf
// eindeutig gesetzt -> neuer Storage-Key ("${type}/${nodeId}/${hash}") -> Cache-Miss ->
// echte Konvertierung je Iteration. `ref.id` bleibt konstant, damit ContentTransferService
// dieselbe Fixture vom Classpath lädt.
function buildNode(cfg) {
  const uniqueHash = `lt-${__VU}-${__ITER}-${Date.now()}`;
  const node = {
    ref: { id: cfg.nodeId, repo: REPO_ID },
    mediatype: cfg.mediatype || '',
    mimetype: cfg.mimetype || '',
    content: { hash: uniqueHash, version: '1.0' },
    properties: {},
  };

  // Drittanbieter-Import-Module (sodix/omega/ddb): Dispatch-relevante Felder ergänzen. Node-
  // Properties sind Listen (der Service liest jeweils [0]). Pro Iteration eindeutige Identifier/
  // remoteIds, damit jede Anfrage ein eigenes externes Objekt abbildet.
  const unique = `${__VU}-${__ITER}`;
  if (cfg.replicationSource) {
    node.properties['ccm:replicationsource'] = [cfg.replicationSource];
    node.properties['ccm:replicationsourceid'] = [`${cfg.replicationSourceId}-${unique}`];
  }
  if (cfg.location) {
    // cclom:location != leer -> hasLocalContent=false, damit omega nicht durchfällt.
    node.properties['cclom:location'] = [cfg.location];
  }
  if (cfg.remote) {
    node.remote = {
      id: `${cfg.remote.id}-${unique}`,
      repository: { repositoryType: cfg.remote.repositoryType },
    };
  }

  return node;
}

function buildBody(cfg) {
  const node = buildNode(cfg);
  const securedNode = encoding.b64encode(JSON.stringify(node));
  return {
    nodeId: node.ref.id,
    repoId: REPO_ID,
    securedNode: securedNode,
    // Signatur wird bei app.security.enabled=false ignoriert, muss aber base64-dekodierbar sein.
    signature: encoding.b64encode('loadtest'),
    signatureAlgorithm: SIGNATURE_ALGORITHM,
    eventType: 'VIEW_MATERIAL',
  };
}

// Sendet einen Render-Request und pollt bis zum terminalen Job-Status. Misst die
// End-to-End-Zeit (rs2_time_to_finish{module}) und Fehlerraten.
// Optional extraTags (e.g. { phase: 'during' }) are added to every metric of this iteration.
export function submitAndPoll(moduleKey, extraTags) {
  const cfg = moduleConfig(moduleKey);
  const tags = Object.assign({ module: moduleKey }, extraTags || {});
  const start = Date.now();

  const res = http.post(`${baseUrl()}/public/renderdata`, JSON.stringify(buildBody(cfg)), {
    headers: { 'Content-Type': 'application/json' },
    tags: Object.assign({ name: 'renderdata' }, tags),
  });

  const submitOk = check(res, { 'renderdata 200': (r) => r.status === 200 }, tags);
  submitErrors.add(!submitOk, tags);
  if (!submitOk) {
    jobErrors.add(true, tags);
    return;
  }

  let jobId = null;
  try {
    jobId = res.json('jobId');
  } catch (e) {
    // ignorieren — jobId bleibt null
  }

  // Sofort-Antwort ohne Job (Cache-Hit / no-conversion): als Erfolg mit Latenz der Antwort werten.
  if (!jobId) {
    timeToFinish.add(Date.now() - start, tags);
    jobErrors.add(false, tags);
    return;
  }

  const status = pollJob(jobId, tags);
  const finished = status === 'FINISHED';
  timeToFinish.add(Date.now() - start, tags);
  jobErrors.add(!finished, tags);
}

function pollJob(jobId, tags) {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const res = http.get(`${baseUrl()}/public/job?jobId=${encodeURIComponent(jobId)}`, {
      tags: Object.assign({ name: 'job' }, tags),
    });
    if (res.status === 200) {
      let status = null;
      try {
        status = res.json('status');
      } catch (e) {
        // ignore parse error, retry
      }
      if (status && TERMINAL.indexOf(status) !== -1) {
        return status;
      }
    }
    sleep(POLL_INTERVAL_MS / 1000);
  }
  return 'TIMEOUT';
}

// Submits `count` render requests for a module with several qualities (video) back to back and polls
// all jobs until they are terminal. Records per job when the first and when each resolution reports
// FINISHED (rs2_video_first_quality, rs2_video_quality_ready{quality}); rs2_time_to_finish is then the
// time until the last resolution. Returns the per-job results.
export function submitAndTrackQualities(moduleKey, count, timeoutMs, pollIntervalMs) {
  const cfg = moduleConfig(moduleKey);
  const tags = { module: moduleKey };
  const jobs = [];
  for (let i = 0; i < count; i++) {
    const start = Date.now();
    const res = http.post(`${baseUrl()}/public/renderdata`, JSON.stringify(buildBody(cfg)), {
      headers: { 'Content-Type': 'application/json' },
      tags: Object.assign({ name: 'renderdata' }, tags),
    });
    const ok = check(res, { 'renderdata 200': (r) => r.status === 200 }, tags);
    submitErrors.add(!ok, tags);
    let jobId = null;
    try {
      jobId = ok ? res.json('jobId') : null;
    } catch (e) {
      // ignorieren — jobId bleibt null
    }
    if (!jobId) {
      jobErrors.add(true, tags);
      continue;
    }
    jobs.push({ jobId, start, ready: {}, status: null });
  }

  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline && jobs.some((j) => j.status === null)) {
    for (const job of jobs.filter((j) => j.status === null)) {
      const res = http.get(`${baseUrl()}/public/job?jobId=${encodeURIComponent(job.jobId)}`, {
        tags: Object.assign({ name: 'job' }, tags),
      });
      if (res.status !== 200) continue;
      let body = null;
      try {
        body = res.json();
      } catch (e) {
        continue;
      }
      const now = Date.now();
      for (const q of body.jobs || []) {
        if (q.status === 'FINISHED' && q.quality > 0 && job.ready[q.quality] === undefined) {
          job.ready[q.quality] = now - job.start;
          videoQualityReady.add(job.ready[q.quality], Object.assign({ quality: String(q.quality) }, tags));
          if (Object.keys(job.ready).length === 1) videoFirstQuality.add(job.ready[q.quality], tags);
          console.log(`video job ${job.jobId}: ${q.quality}p ready after ${(job.ready[q.quality] / 1000).toFixed(0)} s`);
        }
      }
      if (TERMINAL.indexOf(body.status) !== -1) {
        job.status = body.status;
        timeToFinish.add(now - job.start, tags);
        jobErrors.add(body.status !== 'FINISHED', tags);
      }
    }
    sleep(pollIntervalMs / 1000);
  }
  for (const job of jobs.filter((j) => j.status === null)) {
    job.status = 'TIMEOUT';
    jobErrors.add(true, tags);
  }
  return jobs;
}
