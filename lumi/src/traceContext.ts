import { AsyncLocalStorage } from 'async_hooks';
import { randomBytes } from 'crypto';
import createDebug from 'debug';
import { Request, Response, NextFunction } from 'express';

interface TraceStore {
    traceId: string;
}

/**
 * Holds the trace id of the request currently being handled so it can be attached to every log line.
 *
 * In the Istio service mesh the Envoy sidecars generate and report the trace spans; the rendering
 * service forwards the b3 trace headers to Lumi. We adopt that trace id here (or generate a
 * b3-conformant one when none is present) and expose it through this {@link AsyncLocalStorage} so the
 * whole request — including async H5P library calls — can be correlated in the logs.
 */
export const traceStore = new AsyncLocalStorage<TraceStore>();

/**
 * Prepend the active trace id to every log line. Lumi's own code and the `@lumieducation/h5p-*`
 * libraries all log through the shared `debug` module, so patching its formatter once covers all
 * output. Lines logged outside of a request (e.g. at startup) simply have no trace id.
 */
let patched = false;
function patchDebugWithTraceId(): void {
    if (patched) {
        return;
    }
    patched = true;
    const originalFormatArgs = createDebug.formatArgs;
    createDebug.formatArgs = function (args: any[]): void {
        originalFormatArgs.call(this, args);
        const traceId = traceStore.getStore()?.traceId;
        if (traceId) {
            args[0] = `[traceId=${traceId}] ${args[0]}`;
        }
    };
}
patchDebugWithTraceId();

const firstHeaderValue = (value: string | string[] | undefined): string | undefined =>
    Array.isArray(value) ? value[0] : value;

/**
 * Extracts the trace id from the incoming request headers, matching the formats Istio and the
 * rendering service use: the single `b3` header (`traceId-spanId-...`), the multi-header
 * `x-b3-traceid`, the W3C `traceparent` (`00-traceId-spanId-flags`), or the explicit `TRACE` header.
 * Falls back to a freshly generated 128-bit hex id so direct/local requests stay correlatable.
 */
export function extractTraceId(req: Request): string {
    const b3 = firstHeaderValue(req.headers['b3']);
    if (b3) {
        const traceId = b3.split('-')[0];
        if (traceId) {
            return traceId;
        }
    }

    const xB3TraceId = firstHeaderValue(req.headers['x-b3-traceid']);
    if (xB3TraceId) {
        return xB3TraceId;
    }

    const traceparent = firstHeaderValue(req.headers['traceparent']);
    if (traceparent) {
        const parts = traceparent.split('-');
        if (parts.length >= 2 && parts[1]) {
            return parts[1];
        }
    }

    const trace = firstHeaderValue(req.headers['trace']);
    if (trace) {
        return trace;
    }

    return randomBytes(16).toString('hex');
}

/**
 * Express middleware: derive the trace id for the request and run the remaining handler chain within
 * the trace context, so every log line emitted while handling the request carries the trace id.
 */
export function traceContextMiddleware(req: Request, res: Response, next: NextFunction): void {
    const traceId = extractTraceId(req);
    traceStore.run({ traceId }, () => next());
}
