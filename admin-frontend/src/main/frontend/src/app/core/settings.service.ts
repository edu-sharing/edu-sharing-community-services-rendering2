import { Injectable, signal } from '@angular/core';

const POLL_KEY = 'rs2-admin-poll-interval';

export interface IntervalOption {
  label: string;
  value: number;
}

/**
 * Persistent UI settings. Currently: the polling interval (0 = off).
 */
@Injectable({ providedIn: 'root' })
export class SettingsService {
  readonly intervalOptions: IntervalOption[] = [
    { label: 'Off', value: 0 },
    { label: '2 s', value: 2000 },
    { label: '5 s', value: 5000 },
    { label: '10 s', value: 10000 },
    { label: '30 s', value: 30000 },
    { label: '60 s', value: 60000 },
  ];

  readonly pollIntervalMs = signal<number>(this.loadInterval());

  setPollInterval(ms: number): void {
    this.pollIntervalMs.set(ms);
    localStorage.setItem(POLL_KEY, String(ms));
  }

  private loadInterval(): number {
    const raw = localStorage.getItem(POLL_KEY);
    const parsed = raw !== null ? Number(raw) : NaN;
    return Number.isFinite(parsed) ? parsed : 5000;
  }
}
