// DDB-Import-Modul (Deutsche Digitale Bibliothek) gegen die WireMock-Mock-API
// (rendering2-thirdparty-mock). Der DDB-Receiver macht pro Job zwei API-Calls (REST + IIIF),
// beide werden gemockt. Der Dispatch erfolgt über node.remote.repository.repositoryType=DDB.
import { moduleOptions } from '../lib/profiles.js';
import { thresholds } from '../lib/thresholds.js';
import { submitAndPoll } from '../lib/renderdata.js';

export const options = Object.assign({ thresholds }, moduleOptions());

export default function () {
  submitAndPoll('ddb');
}
