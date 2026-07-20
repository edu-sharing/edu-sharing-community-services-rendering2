// OMEGA-Import-Modul (DE.FWU) gegen die WireMock-Mock-API (rendering2-thirdparty-mock).
// Die HEAD-URL-Validierung ist im loadtest-Profil deaktiviert (OMEGA.credentials.validateUrls=false).
import { moduleOptions } from '../lib/profiles.js';
import { thresholds } from '../lib/thresholds.js';
import { submitAndPoll } from '../lib/renderdata.js';

export const options = Object.assign({ thresholds }, moduleOptions());

export default function () {
  submitAndPoll('omega');
}
