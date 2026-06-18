import { Injectable, inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { visibilityTimer } from './polling';
import { SettingsService } from './settings.service';

/**
 * Provides a reactive tick stream for live polling. The interval follows the SettingsService
 * setting (0 = off → a single tick, no repeat). On an interval change the timer is set up
 * anew; it pauses while the tab is in the background (see visibilityTimer).
 */
@Injectable({ providedIn: 'root' })
export class PollingService {
  private readonly settings = inject(SettingsService);

  readonly ticks$: Observable<number> = toObservable(this.settings.pollIntervalMs).pipe(
    switchMap((ms) => (ms > 0 ? visibilityTimer(ms) : of(0))),
  );
}
