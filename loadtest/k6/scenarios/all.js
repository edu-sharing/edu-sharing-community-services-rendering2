// Gemischte Last über die lokal betreibbaren Converter — bildet einen realistischeren
// Mischbetrieb ab (mehrere Queues gleichzeitig unter Last).
//
// Standardmix ohne jupyter/h5p (externe Sidecars). Per Env erweiterbar:
//   -e MIX=image,audio,video,document,pdf,jupyter

import { moduleOptions } from '../lib/profiles.js';
import { thresholds } from '../lib/thresholds.js';
import { submitAndPoll } from '../lib/renderdata.js';

export const options = Object.assign({ thresholds }, moduleOptions());

const MIX = (__ENV.MIX || 'image,audio,video,document,pdf').split(',').map((m) => m.trim());

export default function () {
  // Deterministische, gleichmäßige Verteilung über den Mix — jede Iteration ein anderes Modul.
  const module = MIX[(__VU + __ITER) % MIX.length];
  submitAndPoll(module);
}
