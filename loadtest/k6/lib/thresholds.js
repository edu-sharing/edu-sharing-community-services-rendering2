// Gemeinsame Pass/Fail-Schwellen. Bewusst großzügig — der Lasttest dient primär der
// Beobachtung des Scaling-Verhaltens, nicht einem harten SLA. Per Env anpassbar.

const P95_MS = Number(__ENV.P95_MS || 60000);
const MAX_JOB_ERROR_RATE = Number(__ENV.MAX_JOB_ERROR_RATE || 0.02);

export const thresholds = {
  http_req_failed: ['rate<0.01'],
  rs2_job_errors: [`rate<${MAX_JOB_ERROR_RATE}`],
  rs2_submit_errors: ['rate<0.01'],
  rs2_time_to_finish: [`p(95)<${P95_MS}`],
};
