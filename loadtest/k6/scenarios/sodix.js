// SODIX-Import-Modul gegen die WireMock-Mock-API (rendering2-thirdparty-mock).
// Der Mock antwortet bewusst mit 1–2 s Latenz (siehe loadtest/mocks/wiremock/mappings/sodix.json),
// um die reale Sodix-API nachzubilden — relevant für das Consumer-Scaling der sodix-Queue.
import { moduleOptions } from '../lib/profiles.js';
import { thresholds } from '../lib/thresholds.js';
import { submitAndPoll } from '../lib/renderdata.js';

export const options = Object.assign({ thresholds }, moduleOptions());

export default function () {
  submitAndPoll('sodix');
}
