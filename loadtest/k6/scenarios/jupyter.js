import { moduleOptions } from '../lib/profiles.js';
import { thresholds } from '../lib/thresholds.js';
import { submitAndPoll } from '../lib/renderdata.js';

// Voraussetzung: jupyter-converter-Sidecar läuft (Port 9120) und optionales Modul JUPYTER ist
// aktiv (loadtest-Profil).
export const options = Object.assign({ thresholds }, moduleOptions());

export default function () {
  submitAndPoll('jupyter');
}
