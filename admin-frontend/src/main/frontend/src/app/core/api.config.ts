import { InjectionToken } from '@angular/core';

/**
 * Laufzeit-Konfiguration, die der statische Server (server.mjs) beim Ausliefern in die
 * index.html injiziert (`window.RS2_ADMIN_CONFIG`). Dadurch funktioniert dasselbe Image
 * hinter unterschiedlichen Proxys (nginx-proxy via VIRTUAL_*, externe Apache2-Config, …),
 * ohne dass die Pfade einkompiliert werden müssen.
 */
interface Rs2AdminConfig {
  apiBase?: string;
}

/**
 * Basis-URL-Präfix vor `/admin/...`.
 *
 * - Wert kommt zur Laufzeit aus `window.RS2_ADMIN_CONFIG.apiBase` (gesetzt vom Container,
 *   typischerweise auf den Context-Path des Service, z.B. `/rendering`).
 * - Im lokalen `ng serve` ist das Fenster-Objekt nicht gesetzt → Default `''`; dort übernimmt
 *   der Angular-Dev-Proxy (`proxy.conf.json`) die Weiterleitung von `/admin` an den Service.
 *
 * Ein evtl. vorhandener Schrägstrich am Ende wird entfernt, damit `${base}/admin` sauber bleibt.
 */
export const ADMIN_API_BASE = new InjectionToken<string>('ADMIN_API_BASE', {
  providedIn: 'root',
  factory: () => {
    const cfg = (globalThis as unknown as { RS2_ADMIN_CONFIG?: Rs2AdminConfig }).RS2_ADMIN_CONFIG;
    return (cfg?.apiBase ?? '').replace(/\/$/, '');
  },
});
