import { Observable } from 'rxjs';

/**
 * Liefert einen Tick-Stream für die Live-Aktualisierung (Polling).
 *
 * - Emittiert sofort (Tick 0) und danach alle `intervalMs`.
 * - Pausiert automatisch, solange der Tab im Hintergrund ist (`document.hidden`),
 *   und nimmt beim Zurückwechseln den Betrieb wieder auf.
 *
 * Bewusst als eigenständiger Baustein gekapselt, damit später eine SSE-Quelle den
 * Polling-Stream ersetzen kann, ohne die Feature-Komponenten zu ändern (siehe Plan).
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

/** Standard-Polling-Intervall des Dashboards in Millisekunden. */
export const DEFAULT_POLL_INTERVAL_MS = 5000;
