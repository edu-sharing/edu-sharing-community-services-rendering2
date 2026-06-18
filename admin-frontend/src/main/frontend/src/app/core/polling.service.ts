import { Injectable, inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { visibilityTimer } from './polling';
import { SettingsService } from './settings.service';

/**
 * Liefert einen reaktiven Tick-Stream fürs Live-Polling. Das Intervall folgt der
 * SettingsService-Einstellung (0 = aus → einmaliger Tick, kein Repeat). Bei Intervallwechsel
 * wird der Timer neu aufgesetzt; im Hintergrund-Tab pausiert er (siehe visibilityTimer).
 */
@Injectable({ providedIn: 'root' })
export class PollingService {
  private readonly settings = inject(SettingsService);

  readonly ticks$: Observable<number> = toObservable(this.settings.pollIntervalMs).pipe(
    switchMap((ms) => (ms > 0 ? visibilityTimer(ms) : of(0))),
  );
}
