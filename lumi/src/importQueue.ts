import {Logger} from '@lumieducation/h5p-server'
import {runInImportScope} from './importScope'

const log = new Logger('ImportQueue')

/** Thrown to the caller of {@link ImportQueue.run} when its task ran longer than the deadline. */
export class ImportDeadlineError extends Error {
    constructor(public readonly timeoutMs: number) {
        super(`H5P package import exceeded its deadline of ${timeoutMs} ms`)
        this.name = 'ImportDeadlineError'
    }
}

export interface ImportQueueStatus {
    /** Imports waiting for their turn. */
    waiting: number
    /** Whether an import currently holds the queue. */
    running: boolean
    /** How long the current import has been running; undefined while idle. */
    runningForMs?: number
    /** The current import is past its deadline and still holds the queue (global mode only). */
    overdue: boolean
    /** Imports that were given up on since start. */
    deadlineExceeded: number
}

export interface ImportQueueOptions {
    /** Per-import deadline in ms; 0 disables it. */
    timeoutMs: number
    /**
     * Whether the queue moves on when an import blew its deadline although the task itself is still
     * running. Only safe if imports cannot interfere with each other: with per-package library
     * caches every import has a private staging area and lock provider, whereas imports into the
     * shared global library storage corrupt it when they overlap (see router.ts), so there the queue
     * keeps waiting for the task to settle.
     */
    releaseOnTimeout: boolean
    /**
     * Called when an import exceeded its deadline, before the caller is answered. Used to free what the
     * stuck task is waiting on, so it settles instead of lingering as a zombie.
     */
    onDeadline?: () => void
    now?: () => number
}

/**
 * Runs H5P package imports one at a time.
 *
 * `h5pEditor.uploadPackage` installs all libraries of a package with an unbounded `Promise.all`; two
 * imports at once saturate S3 and, in the global storage, corrupt the library store. The queue also
 * keeps a single import from holding everything up forever: a request that never returns (an S3
 * socket that leaked, a peer that stopped answering) used to freeze every later import, because
 * nothing bounded how long a task may take. The rendering service then timed out one retry after the
 * other while they all piled up behind it.
 */
export class ImportQueue {
    private tail: Promise<void> = Promise.resolve()
    private waiting = 0
    private current?: { startedAt: number; overdue: boolean }
    private deadlineExceeded = 0
    private readonly now: () => number

    constructor(private readonly options: ImportQueueOptions) {
        this.now = options.now ?? Date.now
    }

    /**
     * Runs `task` once everything queued before it has finished. The task executes inside the import
     * scope. Rejects with {@link ImportDeadlineError} if it takes longer than the deadline.
     */
    public run<T>(task: () => Promise<T>): Promise<T> {
        this.waiting++
        const gate = this.tail
        let releaseChain!: () => void
        this.tail = new Promise<void>(resolve => {
            releaseChain = resolve
        })

        return gate.then(async () => {
            this.waiting--
            const state = {startedAt: this.now(), overdue: false}
            this.current = state
            let released = false
            const release = () => {
                if (!released) {
                    released = true
                    if (this.current === state) {
                        this.current = undefined
                    }
                    releaseChain()
                }
            }

            const work = Promise.resolve().then(() => runInImportScope(task))
            // The queue is free again once the work settles, however the caller was answered.
            work.then(release, release)

            const {timeoutMs} = this.options
            if (timeoutMs <= 0) {
                return work
            }
            let timer: ReturnType<typeof setTimeout> | undefined
            const deadline = new Promise<never>((_, reject) => {
                timer = setTimeout(() => {
                    state.overdue = true
                    this.deadlineExceeded++
                    log.error(
                        `Import exceeded its deadline of ${timeoutMs} ms; ` +
                        (this.options.releaseOnTimeout
                            ? 'moving on to the next one.'
                            : 'it keeps holding the queue until it settles.')
                    )
                    try {
                        this.options.onDeadline?.()
                    } catch (error: any) {
                        log.error(`Deadline hook failed: ${error.message}`)
                    }
                    if (this.options.releaseOnTimeout) {
                        release()
                    }
                    reject(new ImportDeadlineError(timeoutMs))
                }, timeoutMs)
            })
            try {
                return await Promise.race([work, deadline])
            } finally {
                clearTimeout(timer)
            }
        })
    }

    public status(): ImportQueueStatus {
        return {
            waiting: this.waiting,
            running: this.current !== undefined,
            runningForMs: this.current ? this.now() - this.current.startedAt : undefined,
            overdue: this.current?.overdue ?? false,
            deadlineExceeded: this.deadlineExceeded
        }
    }
}

/**
 * Process-wide queue; `createImportQueue` is called once at startup with the effective mode.
 */
let importQueue: ImportQueue | undefined

export const createImportQueue = (releaseOnTimeout: boolean, onDeadline?: () => void): ImportQueue => {
    const raw = process.env.H5P_IMPORT_TIMEOUT_MS
    const parsed = Number.parseInt(raw ?? '', 10)
    // 15 minutes. A healthy import takes seconds to a few minutes (750 MB: under a minute, 2 GB: a few
    // minutes on slower storage), so this only fires for an import that is stuck. It should stay above
    // the rendering service's H5P timeout (repository credential "timeout", default 300 s, capped by its
    // 600 s HTTP response timeout), otherwise lumi gives up on imports the service is still waiting for.
    const timeoutMs = Number.isFinite(parsed) && parsed >= 0 ? parsed : 900_000
    if (raw && timeoutMs !== parsed) {
        log.warn(`Invalid H5P_IMPORT_TIMEOUT_MS "${raw}", using ${timeoutMs} ms.`)
    }
    importQueue = new ImportQueue({timeoutMs, releaseOnTimeout, onDeadline})
    log.info(`Import queue ready (deadline ${timeoutMs ? `${timeoutMs} ms` : 'off'}, ` +
        `${releaseOnTimeout ? 'releases' : 'keeps'} the queue on timeout).`)
    return importQueue
}

export const getImportQueueStatus = (): ImportQueueStatus | undefined => importQueue?.status()
