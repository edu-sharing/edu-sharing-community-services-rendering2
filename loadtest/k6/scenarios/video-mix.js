// A large video under concurrent "simple" load: shows how long the video encodes take themselves and
// how much they slow down the other conversions running on the same pods.
//
// Flow:
//   - `simple`: constant arrival rate RATE/s from MIX (default image,document,pdf) for the whole run.
//     Iterations started before WARMUP_S are tagged phase=before (reference without video), later
//     ones phase=during.
//   - `video`: starts after WARMUP_S, submits VIDEO_COUNT videos (module VIDEO_MODULE, default
//     videoLarge = classpath:loadtest/large-video.mp4) and tracks when each resolution is done.
//     Once all videos are terminal the test is aborted, so phase=during covers exactly the window in
//     which video encodes run.
//
// Against the local cluster of local-cluster.sh, e.g.:
//   k6 run -e BASE_URL=http://localhost:8085,http://localhost:8086 -e RATE=2 loadtest/k6/scenarios/video-mix.js
//
// Results: rs2_video_first_quality / rs2_video_quality_ready{quality} / rs2_time_to_finish{module:videoLarge}
// for the video, rs2_time_to_finish{phase:before|during} and rs2_job_errors{phase:...} for the simple
// load (a job exceeding POLL_TIMEOUT_MS counts as an error - like the timeout in a client script).

import exec from 'k6/execution';
import { submitAndPoll, submitAndTrackQualities } from '../lib/renderdata.js';

const RATE = Number(__ENV.RATE || 2);
const WARMUP_S = Number(__ENV.WARMUP_S || 60);
const MAX_DURATION = __ENV.MAX_DURATION || '60m';
const VIDEO_MODULE = __ENV.VIDEO_MODULE || 'videoLarge';
const VIDEO_COUNT = Number(__ENV.VIDEO_COUNT || 1);
const VIDEO_TIMEOUT_MS = Number(__ENV.VIDEO_TIMEOUT_MS || 3600000);
const MIX = (__ENV.MIX || 'image,document,pdf').split(',').map((m) => m.trim());

export const options = {
  scenarios: {
    simple: {
      executor: 'constant-arrival-rate',
      exec: 'simple',
      rate: RATE,
      timeUnit: '1s',
      duration: MAX_DURATION,
      preAllocatedVUs: 50,
      // Under contention one iteration lasts up to POLL_TIMEOUT_MS; enough VUs to hold the arrival
      // rate (otherwise the test measures k6 instead of the service).
      maxVUs: 600,
    },
    video: {
      executor: 'per-vu-iterations',
      exec: 'video',
      vus: 1,
      iterations: 1,
      startTime: `${WARMUP_S}s`,
      maxDuration: MAX_DURATION,
    },
  },
  // Thresholds mainly make k6 print the per-phase/per-quality sub-metrics in the summary.
  thresholds: {
    'rs2_time_to_finish{phase:before}': ['p(95)<120000'],
    'rs2_time_to_finish{phase:during}': ['p(95)<120000'],
    'rs2_job_errors{phase:before}': ['rate<0.01'],
    'rs2_job_errors{phase:during}': ['rate<0.01'],
    [`rs2_time_to_finish{module:${VIDEO_MODULE}}`]: ['max>0'],
    rs2_video_first_quality: ['max>0'],
    'rs2_video_quality_ready{quality:480}': ['max>0'],
    'rs2_video_quality_ready{quality:720}': ['max>0'],
    'rs2_video_quality_ready{quality:1080}': ['max>0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'max', 'count'],
};

export function simple() {
  const phase = exec.instance.currentTestRunDuration < WARMUP_S * 1000 ? 'before' : 'during';
  const module = MIX[exec.scenario.iterationInTest % MIX.length];
  submitAndPoll(module, { phase });
}

export function video() {
  const jobs = submitAndTrackQualities(VIDEO_MODULE, VIDEO_COUNT, VIDEO_TIMEOUT_MS, 2000);
  for (const job of jobs) {
    const ready = Object.keys(job.ready)
      .map((q) => `${q}p=${(job.ready[q] / 1000).toFixed(0)}s`)
      .join(' ');
    console.log(`video job ${job.jobId}: ${job.status} ${ready}`);
  }
  // All videos terminal -> close the "during" window.
  exec.test.abort('video jobs terminal');
}
