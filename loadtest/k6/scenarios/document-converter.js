// Direct load test of the document-converter (POST /conversion), bypassing the rendering service.
// Isolates the LibreOffice parallelism (LIBREOFFICE_PORT_NUMBERS) from queue/S3/Mongo effects.
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { moduleOptions } from '../lib/profiles.js';

const CONVERTER_URL = __ENV.CONVERTER_URL || 'http://localhost:8081';
const FORMAT = __ENV.FORMAT || 'pdf';
const FILE = open('../fixtures/document.docx', 'b');

const convDuration = new Trend('dc_conversion_duration', true);
const convErrors = new Rate('dc_conversion_errors');

export const options = Object.assign({
  thresholds: {
    dc_conversion_errors: ['rate<0.01'],
    dc_conversion_duration: [`p(95)<${Number(__ENV.P95_MS || 60000)}`],
  },
}, moduleOptions());

export default function () {
  const res = http.post(`${CONVERTER_URL}/conversion?format=${FORMAT}`, {
    file: http.file(FILE, 'document.docx'),
  }, { timeout: '600s' });
  const ok = check(res, { 'status 200': (r) => r.status === 200, 'non-empty body': (r) => r.body && r.body.length > 0 });
  convErrors.add(!ok);
  convDuration.add(res.timings.duration);
}
