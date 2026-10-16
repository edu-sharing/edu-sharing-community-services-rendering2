import {Logger} from '@lumieducation/h5p-server'
import * as promClient from 'prom-client'

const log = new Logger('Shutdown')

/** Default grace time of a shutdown when SHUTDOWN_TIMEOUT_MS is not set (matches Docker's default stop timeout). */
export const DEFAULT_SHUTDOWN_TIMEOUT_MS = 10_000

/**
 * Reads how long a shutdown may wait for in-flight requests (typically a running package import) before the
 * process is killed. Must stay below the pod's terminationGracePeriodSeconds, otherwise the kubelet sends SIGKILL
 * first; the Helm chart derives it from that value.
 */
export const readShutdownTimeoutMs = (raw: string | undefined = process.env.SHUTDOWN_TIMEOUT_MS): number => {
    if (raw === undefined || raw.trim() === '') {
        return DEFAULT_SHUTDOWN_TIMEOUT_MS
    }
    const parsed = Number.parseInt(raw, 10)
    if (!Number.isFinite(parsed) || parsed <= 0) {
        log.warn(`Invalid SHUTDOWN_TIMEOUT_MS "${raw}", using ${DEFAULT_SHUTDOWN_TIMEOUT_MS} ms.`)
        return DEFAULT_SHUTDOWN_TIMEOUT_MS
    }
    return parsed
}

const describe = (value: unknown): string =>
    value instanceof Error ? (value.stack ?? value.message) : String(value)

/**
 * Process-level safety nets.
 *
 * - `unhandledRejection`: since Node 15 an unhandled rejection terminates the process. A package import that blew its
 *   deadline is abandoned, not cancelled (see importQueue.ts), so it may reject long after its request was answered
 *   with 504 - that must not take down every other request. The rejection is logged and counted instead.
 * - `uncaughtException`: the process state is unknown, so it is not carried on. `onFatal` starts the controlled
 *   shutdown (drain, flush, exit with a failure code).
 */
export const installProcessGuards = (onFatal: (error: unknown) => void): void => {
    const unhandled = new promClient.Counter({
        name: 'lumi_unhandled_rejections_total',
        help: 'Promise rejections nobody handled (e.g. an abandoned package import failing late); logged, not fatal.'
    })
    process.on('unhandledRejection', reason => {
        unhandled.inc()
        log.error(`Unhandled promise rejection (ignored): ${describe(reason)}`)
    })
    process.on('uncaughtException', error => {
        log.error(`Uncaught exception, shutting down: ${describe(error)}`)
        onFatal(error)
    })
}
