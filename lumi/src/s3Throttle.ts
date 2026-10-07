import {inImportScope} from './importScope'
import {S3PoolWatchdog} from './s3Pool'

/** Counting semaphore with FIFO hand-over. */
export class Semaphore {
    private available: number
    private readonly waiting: Array<() => void> = []

    constructor(permits: number) {
        this.available = Math.max(1, permits)
    }

    public async acquire(): Promise<void> {
        if (this.available > 0) {
            this.available--
            return
        }
        await new Promise<void>(resolve => this.waiting.push(resolve))
    }

    public release(): void {
        const next = this.waiting.shift()
        if (next) {
            // Hand the permit straight over instead of making it available first, so a newcomer
            // cannot overtake the one that has been waiting.
            next()
        } else {
            this.available++
        }
    }
}

/**
 * Anything with the middleware stack of an AWS SDK v3 client. Typed structurally so this file does not
 * depend on which copy of `@aws-sdk/client-s3` the storages bring along.
 */
interface MiddlewareStackHolder {
    middlewareStack: {
        add(middleware: (next: (args: any) => Promise<any>) => (args: any) => Promise<any>,
            options: { step: 'initialize'; name: string }): void
    }
}

/**
 * Installs two behaviours on an S3 client:
 *
 * - **Import throttle.** A package import touches every file of every library and of the content at
 *   once (an unbounded `Promise.all`, >1500 requests). With a shared pool of 256 sockets that alone
 *   starves every player request for as long as the import runs. Requests made inside the import scope
 *   (see importScope.ts) therefore wait for one of `importConcurrency` permits first. They queue here,
 *   *before* the SDK creates the request, so the SDK's connection timer - which also covers the wait
 *   for a free socket - is not running while they wait. Requests outside the import are never held.
 * - **Progress signal** for the pool watchdog: every request that settles, whatever the outcome.
 *
 * The step is `initialize`, the outermost one: a permit is held for the whole operation including
 * the SDK's own retries. For a download that is until the response headers arrive, not for the body.
 */
export const installS3Middleware = (
    client: MiddlewareStackHolder,
    importConcurrency: number,
    watchdog?: S3PoolWatchdog
): void => {
    const semaphore = new Semaphore(importConcurrency)
    client.middlewareStack.add(
        next => async args => {
            const throttled = inImportScope()
            if (throttled) {
                await semaphore.acquire()
            }
            try {
                return await next(args)
            } finally {
                if (throttled) {
                    semaphore.release()
                }
                watchdog?.noteProgress()
            }
        },
        {step: 'initialize', name: 'lumiS3ImportThrottle'}
    )
}
