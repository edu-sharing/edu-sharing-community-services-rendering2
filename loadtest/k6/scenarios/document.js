import { moduleOptions } from '../lib/profiles.js';
import { thresholds } from '../lib/thresholds.js';
import { submitAndPoll } from '../lib/renderdata.js';

export const options = Object.assign({ thresholds }, moduleOptions());

export default function () {
  submitAndPoll('document');
}
