import { InjectionToken } from '@angular/core';

/**
 * Runtime configuration that the static server (server.mjs) injects into index.html when
 * serving it (`window.RS2_ADMIN_CONFIG`). This lets the same image run behind different
 * proxies (nginx-proxy via VIRTUAL_*, an external Apache2 config, …) without the paths
 * having to be compiled in.
 */
interface Rs2AdminConfig {
  apiBase?: string;
}

/**
 * Base URL prefix in front of `/admin/...`.
 *
 * - The value comes at runtime from `window.RS2_ADMIN_CONFIG.apiBase` (set by the container,
 *   typically to the service's context path, e.g. `/rendering`).
 * - In a local `ng serve` the window object is not set → default `''`; there the Angular dev
 *   proxy (`proxy.conf.json`) handles forwarding `/admin` to the service.
 *
 * A trailing slash, if present, is stripped so that `${base}/admin` stays clean.
 */
export const ADMIN_API_BASE = new InjectionToken<string>('ADMIN_API_BASE', {
  providedIn: 'root',
  factory: () => {
    const cfg = (globalThis as unknown as { RS2_ADMIN_CONFIG?: Rs2AdminConfig }).RS2_ADMIN_CONFIG;
    return (cfg?.apiBase ?? '').replace(/\/$/, '');
  },
});
