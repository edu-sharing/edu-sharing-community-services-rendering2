import * as http from 'http'
import * as https from 'https'
import {Logger} from '@lumieducation/h5p-server'

const log = new Logger('S3Pool')

export interface S3PoolStatus {
    /** Sockets currently checked out of the pool. */
    inUse: number
    /** Requests waiting for a free socket. */
    queued: number
    maxSockets: number
    /** How long the pool has been full without a single request settling; 0 if it is not full. */
    stalledForMs: number
    /** The pool has been full without progress for at least half the stall time, i.e. recovery is near. */
    stalled: boolean
    /** Times the watchdog had to destroy the pool's sockets. */
    recoveries: number
}

export interface S3PoolWatchdogOptions {
    /** A full pool without progress for this long counts as stuck. */
    stallMs: number
    checkIntervalMs: number
    now?: () => number
}

/**
 * Frees the S3 connection pool when it is full of sockets that will never come back.
 *
 * A socket only returns to the pool once its response was read to the end or destroyed. A body that
 * is never consumed, or a peer that stops answering, keeps its socket checked out for good, and the
 * AWS SDK has no timeout by default (see createH5PEditor.ts for why the SDK's own are left off).
 * Once all of them are gone every further request, import and player alike, queues forever, and the
 * only symptom is the `socket usage at capacity` warning, which the SDK emits only after the queue has
 * grown to twice the pool size - in practice an hour into the outage.
 *
 * The watchdog treats the pool as stuck when it is full, has waiting requests and not one request has
 * settled for `stallMs`. A busy but healthy pool settles requests all the time, so this does not fire
 * under load. Recovery destroys every socket of the agents: requests in flight on them fail and are
 * retried by the SDK, and the queued ones get fresh connections.
 */
export class S3PoolWatchdog {
    private lastProgressAt: number
    private fullSince?: number
    private recoveries = 0
    private timer?: ReturnType<typeof setInterval>
    private readonly now: () => number

    constructor(
        private readonly agents: Array<http.Agent | https.Agent>,
        private readonly options: S3PoolWatchdogOptions
    ) {
        this.now = options.now ?? Date.now
        this.lastProgressAt = this.now()
    }

    /** Called whenever an S3 request settled, successfully or not. */
    public noteProgress(): void {
        this.lastProgressAt = this.now()
    }

    public start(): void {
        if (this.timer || this.options.stallMs <= 0) {
            return
        }
        this.timer = setInterval(() => this.check(), this.options.checkIntervalMs)
        // Never keep the process alive just for this.
        this.timer.unref()
    }

    public stop(): void {
        if (this.timer) {
            clearInterval(this.timer)
            this.timer = undefined
        }
    }

    public check(): void {
        const usage = this.usage()
        if (!usage.full) {
            this.fullSince = undefined
            return
        }
        const now = this.now()
        this.fullSince ??= now
        const stalledFor = now - Math.max(this.fullSince, this.lastProgressAt)
        if (stalledFor < this.options.stallMs) {
            return
        }
        this.destroySockets(
            `pool stuck for ${stalledFor} ms (${usage.inUse}/${usage.maxSockets} sockets in use, ` +
            `${usage.queued} requests queued, nothing settled)`
        )
    }

    /**
     * Destroys every socket of the pool. Requests in flight on them fail (the SDK retries them) and the
     * queued ones get fresh connections. Also used when an import blew its deadline: a single request
     * that never returns holds just one socket, so the pool is nowhere near full and the stall check
     * would never notice it.
     */
    public destroySockets(reason: string): void {
        this.recoveries++
        log.error(`S3 ${reason} - destroying the pool's sockets. Recovery #${this.recoveries}.`)
        for (const agent of this.agents) {
            agent.destroy()
        }
        this.fullSince = undefined
        this.lastProgressAt = this.now()
    }

    public status(): S3PoolStatus {
        const {full, ...usage} = this.usage()
        const now = this.now()
        const stalledForMs = full ? Math.max(0, now - Math.max(this.fullSince ?? now, this.lastProgressAt)) : 0
        return {
            ...usage,
            stalledForMs,
            stalled: this.options.stallMs > 0 && stalledForMs >= this.options.stallMs / 2,
            recoveries: this.recoveries
        }
    }

    /**
     * Pool usage as the SDK sees it: a pool is full per origin and agent, not summed over them - the
     * HTTP and HTTPS agents each have their own limit and only one of them is ever used. Reports the
     * fullest origin (or, if none is full, the busiest one).
     */
    private usage(): { inUse: number; queued: number; maxSockets: number; full: boolean } {
        let result = {inUse: 0, queued: 0, maxSockets: 0, full: false}
        for (const agent of this.agents) {
            const maxSockets = typeof agent.maxSockets === 'number' && Number.isFinite(agent.maxSockets)
                ? agent.maxSockets : 0
            const origins = new Set([...Object.keys(agent.sockets), ...Object.keys(agent.requests)])
            for (const origin of origins) {
                const inUse = agent.sockets[origin]?.length ?? 0
                const queued = agent.requests[origin]?.length ?? 0
                const full = maxSockets > 0 && inUse >= maxSockets && queued > 0
                if ((full && !result.full) || (full === result.full && inUse + queued > result.inUse + result.queued)) {
                    result = {inUse, queued, maxSockets: maxSockets || result.maxSockets, full}
                }
            }
            if (result.maxSockets === 0) {
                result.maxSockets = maxSockets
            }
        }
        return result
    }
}

let watchdog: S3PoolWatchdog | undefined

export const registerS3PoolWatchdog = (instance: S3PoolWatchdog): void => {
    watchdog = instance
}

/** Frees the S3 pool's sockets, if a pool watchdog is registered. */
export const resetS3Pool = (reason: string): void => watchdog?.destroySockets(reason)

export const getS3PoolStatus = (): S3PoolStatus | undefined => watchdog?.status()
