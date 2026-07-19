// Lastprofile für die verschiedenen Beobachtungsziele. Auswahl über `-e PROFILE=<name>`.
//
//  smoke   — 1 VU, wenige Iterationen: schneller Funktions-Check pro Converter.
//  ramping — langsam steigende VUs: macht die Consumer-Auto-Scaling-Kurve (Ebene 1) sichtbar.
//  arrival — konstante Ankunftsrate über der Kapazität: Sättigung/Backpressure, Queue wächst.
//  burst   — kurzer Peak, dann Stille: Ramp-up-/Teardown-Verhalten der Auto-Skalierung.
//
// Feinsteuerung ebenfalls per Env: VUS, RATE, DURATION, RAMP.

const PROFILE = __ENV.PROFILE || 'ramping';
const VUS = Number(__ENV.VUS || 20);
const RATE = Number(__ENV.RATE || 20);
const DURATION = __ENV.DURATION || '2m';
const RAMP = __ENV.RAMP || '30s';

function scenarioFor(profile) {
  switch (profile) {
    case 'smoke':
      return { executor: 'shared-iterations', vus: 1, iterations: Number(__ENV.ITERATIONS || 3), maxDuration: '2m' };
    case 'arrival':
      return {
        executor: 'constant-arrival-rate',
        rate: RATE,
        timeUnit: '1s',
        duration: DURATION,
        preAllocatedVUs: Math.max(VUS, RATE),
        maxVUs: Math.max(VUS, RATE) * 4,
      };
    case 'burst':
      return {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
          { duration: '5s', target: VUS },   // schneller Peak
          { duration: '20s', target: VUS },  // Peak halten
          { duration: '5s', target: 0 },     // abrupt beenden -> Teardown beobachten
          { duration: '40s', target: 0 },    // Stille: Consumer sollten zurückskalieren
        ],
        gracefulRampDown: '0s',
      };
    case 'ramping':
    default:
      return {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
          { duration: RAMP, target: VUS },
          { duration: DURATION, target: VUS },
          { duration: RAMP, target: 0 },
        ],
        gracefulRampDown: '10s',
      };
  }
}

// Baut das k6-`options`-Objekt für ein einzelnes Modul-Szenario.
export function moduleOptions() {
  return {
    scenarios: {
      [PROFILE]: scenarioFor(PROFILE),
    },
  };
}

export { PROFILE, VUS, RATE, DURATION, RAMP };
