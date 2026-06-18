import { Observable } from 'rxjs';

/**
 * Provides a tick stream for live updates (polling).
 *
 * - Emits immediately (tick 0) and then every `intervalMs`.
 * - Automatically pauses while the tab is in the background (`document.hidden`),
 *   and resumes when switching back.
 *
 * Deliberately encapsulated as a standalone building block so that an SSE source can later
 * replace the polling stream without changing the feature components (see plan).
 */
export function visibilityTimer(intervalMs: number): Observable<number> {
  return new Observable<number>((subscriber) => {
    let counter = 0;
    let handle: ReturnType<typeof setInterval> | null = null;

    const tick = () => subscriber.next(counter++);
    const start = () => {
      if (handle === null) {
        tick();
        handle = setInterval(tick, intervalMs);
      }
    };
    const stop = () => {
      if (handle !== null) {
        clearInterval(handle);
        handle = null;
      }
    };
    const onVisibility = () => (document.hidden ? stop() : start());

    document.addEventListener('visibilitychange', onVisibility);
    start();

    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  });
}

/** Default polling interval of the dashboard in milliseconds. */
export const DEFAULT_POLL_INTERVAL_MS = 5000;
