import { Injectable, computed, signal } from '@angular/core';

const POLL_KEY = 'rs2-admin-poll-interval';
const CONTRAST_KEY = 'rs2-admin-contrast';

export interface IntervalOption {
  label: string;
  value: number;
}

export type ContrastMode = 'auto' | 'high' | 'normal';

export interface ContrastOption {
  label: string;
  value: ContrastMode;
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

  // --- High-contrast / WCAG-AA accessibility mode ---
  readonly contrastOptions: ContrastOption[] = [
    { label: 'Auto', value: 'auto' },
    { label: 'High', value: 'high' },
    { label: 'Normal', value: 'normal' },
  ];

  readonly contrastMode = signal<ContrastMode>(this.loadContrast());

  /** Tracks the OS `prefers-contrast: more` preference (used when the mode is `auto`). */
  private readonly osHighContrast = signal(this.queryOsHighContrast());

  /**
   * Effective high-contrast state: forced on/off by the user, or — in `auto` — derived from the
   * OS `prefers-contrast: more` preference. Default `auto` keeps the brand look unless the OS
   * (or the user) asks for stronger contrast.
   */
  readonly highContrastActive = computed(
    () => this.contrastMode() === 'high' || (this.contrastMode() === 'auto' && this.osHighContrast()),
  );

  setContrastMode(mode: ContrastMode): void {
    this.contrastMode.set(mode);
    localStorage.setItem(CONTRAST_KEY, mode);
  }

  private loadContrast(): ContrastMode {
    const raw = localStorage.getItem(CONTRAST_KEY);
    return raw === 'high' || raw === 'normal' || raw === 'auto' ? raw : 'auto';
  }

  private queryOsHighContrast(): boolean {
    const mql = typeof window !== 'undefined' ? window.matchMedia?.('(prefers-contrast: more)') : null;
    if (mql) {
      mql.addEventListener('change', (e) => this.osHighContrast.set(e.matches));
      return mql.matches;
    }
    return false;
  }
}
