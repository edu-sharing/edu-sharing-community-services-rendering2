import {access, mkdir, readdir, readFile, rename, rm, stat, writeFile} from 'fs/promises'
import path from 'path'
import {randomUUID} from 'crypto'
import {fsImplementations, ILibraryStorage, Logger} from '@lumieducation/h5p-server'
import {CacheQuotaExceededError, LibraryStagingArea, PackageLibraryCacheUsage, PackageLibraryStore} from './types'

const log = new Logger('FsPackageLibraryStore')

/**
 * Staging areas live under a dot-prefixed directory so that {@link isValidPackageId} - which
 * requires an alphanumeric first character - can never address them as a package.
 */
const STAGING_DIR = '.staging'

/**
 * The cache size remembered between runs. Lives in the cache root next to the packages; the
 * dot-prefix keeps it, like the staging area, from ever being addressable as a package id.
 */
const USAGE_FILE = '.usage.json'
const USAGE_FILE_TMP = `${USAGE_FILE}.tmp`

/** How long after a change the remembered size is written; bursts of imports share one write. */
const SAVE_DELAY_MS = 5_000

/**
 * While the cache is being measured, imports go ahead on the remembered size - unless it says the
 * cache is nearly full. Then the remembered value is too uncertain to decide on (it can be off by
 * whatever happened since the last write, e.g. a crash), so the import waits for the measurement.
 */
const NEARLY_FULL = 0.9

/**
 * Package ids reach us straight from the request URL, and are turned into a directory name. Anything
 * that could escape the cache root (`..`, absolute paths, separators) has to be rejected before it
 * ever touches the filesystem. Mongo ObjectId hex strings - what our package ids actually are - pass.
 */
export const isValidPackageId = (packageId: string): boolean =>
    typeof packageId === 'string' &&
    packageId.length <= 128 &&
    /^[A-Za-z0-9][A-Za-z0-9_-]*$/.test(packageId)

/**
 * Keeps each imported package's libraries in its own directory below a single root:
 *
 * ```
 * <root>/.staging/<uuid>/            an import in flight
 * <root>/<packageId>/<ubername>/     a published package
 * ```
 *
 * A package directory is just a stock `FileLibraryStorage`. That is the whole trick: the storage is
 * already scoped to one directory, so pointing one instance per package at its own directory gives
 * the exact-patch isolation, and we inherit its filename validation (which the H5P HTTP layer
 * deliberately omits) instead of reimplementing it.
 */
export default class FsPackageLibraryStore implements PackageLibraryStore {
    /**
     * @param root the directory holding all per-package caches
     * @param maxCachedStorages how many `FileLibraryStorage` instances to keep around; they are
     * tiny (a path plus a few methods), the bound only stops unbounded growth
     */
    private constructor(
        private readonly root: string,
        private readonly maxCachedStorages: number,
        private readonly quotaBytes: number
    ) {}

    /**
     * Running total of the published packages' size, kept up to date by {@link promote} and
     * {@link remove} so the quota check never has to walk the whole cache.
     */
    private usedBytes = 0

    /** Whether `usedBytes` stems from a previous run's record or a finished measurement, not a guess of 0. */
    private usageKnown = false

    /** The measurement of the whole cache while it is running. */
    private measuring?: Promise<void>

    /** Net change from imports/removals during the measurement, which it may or may not have seen. */
    private changeDuringMeasurement = 0

    private dirty = false
    private saveTimer?: ReturnType<typeof setTimeout>

    /** Cheap LRU of storages, most recently used last (`Map` preserves insertion order). */
    private readonly storages = new Map<string, ILibraryStorage>()

    /**
     * Prepares the cache root. Any staging directory found here is a leftover of an import that died
     * mid-flight - it can never be referenced again, so it is swept on startup.
     *
     * Returns as soon as the root is ready: measuring the cache walks every file in it (minutes for
     * tens of GB of small files) and used to run before the server started listening, which left lumi
     * unreachable - lookups and players included - after every restart. The size from the previous run
     * is taken over right away and the walk runs in the background; see {@link awaitUsage}.
     */
    public static async create(
        root: string,
        options: {quotaBytes?: number; maxCachedStorages?: number} = {}
    ): Promise<FsPackageLibraryStore> {
        const store = new FsPackageLibraryStore(root, options.maxCachedStorages ?? 256, options.quotaBytes ?? 0)
        await mkdir(root, {recursive: true})
        await rm(store.stagingRoot(), {recursive: true, force: true})
        await mkdir(store.stagingRoot(), {recursive: true})
        const remembered = await store.loadRememberedUsage()
        if (remembered !== undefined) {
            store.usedBytes = remembered
            store.usageKnown = true
        }
        store.startMeasuring()
        log.info(
            `Per-package library cache ready at ${root} ` +
            `(${remembered === undefined ? 'size unknown' : `${store.usedBytes} bytes remembered`}` +
            `${store.quotaBytes ? ` of ${store.quotaBytes}` : ', no quota'}; measuring it in the background).`
        )
        return store
    }

    public async createStaging(): Promise<LibraryStagingArea> {
        const id = randomUUID()
        // The constructor creates the directory, which is what we want here.
        const storage = new fsImplementations.FileLibraryStorage(this.stagingDir(id))
        log.debug(`Opened library staging area ${id}.`)
        return {id, storage}
    }

    public async promote(staging: LibraryStagingArea, packageId: string): Promise<number> {
        if (!isValidPackageId(packageId)) {
            throw new Error(`Refusing to store libraries under invalid package id '${packageId}'.`)
        }
        const target = this.packageDir(packageId)
        const incoming = await directorySize(this.stagingDir(staging.id))
        // A re-import replaces the package's libraries, so only the difference counts against the quota.
        const replaced = await directorySize(target)

        if (this.quotaBytes > 0 && this.usedBytes - replaced + incoming > this.quotaBytes) {
            throw new CacheQuotaExceededError(this.usedBytes, this.quotaBytes, incoming - replaced)
        }

        // A re-import of the same package must replace its libraries wholesale: `rename` will not
        // overwrite a non-empty directory.
        await rm(target, {recursive: true, force: true})
        await rename(this.stagingDir(staging.id), target)
        this.adjustUsage(incoming - replaced)
        // Drop a storage still pointing at the previous directory inode.
        this.storages.delete(packageId)
        log.info(`Published libraries of package ${packageId} (${incoming} bytes, ${this.usedBytes} in use).`)
        return incoming
    }

    public async discard(staging: LibraryStagingArea): Promise<void> {
        await rm(this.stagingDir(staging.id), {recursive: true, force: true})
        log.debug(`Discarded library staging area ${staging.id}.`)
    }

    public async storageFor(packageId: string): Promise<ILibraryStorage | undefined> {
        if (!isValidPackageId(packageId)) {
            return undefined
        }
        const dir = this.packageDir(packageId)
        // Checked on every call, cache hit or not. A cached storage is just a path plus methods - it
        // says nothing about the directory still being there, and the directory can disappear behind
        // our back (a wiped volume, a pod rescheduled without one, a manual delete). Serving a stale
        // one makes the caller believe the package is fine, and the failure then surfaces deep inside
        // the player as an ENOENT on readdir instead of as "these libraries are gone".
        try {
            await access(dir)
        } catch {
            // No libraries for this package - the caller falls back to the global storage, or, for a
            // package-scoped import, repairs the mapping (see isPackageLibrariesMissing in router.ts).
            this.storages.delete(packageId)
            return undefined
        }
        const cached = this.storages.get(packageId)
        if (cached) {
            this.storages.delete(packageId)
            this.storages.set(packageId, cached)
            return cached
        }
        // Only construct after the directory is known to exist: `FileLibraryStorage`'s constructor
        // creates it, which would let any bogus id litter the cache root with empty directories.
        const storage = new fsImplementations.FileLibraryStorage(dir)
        this.remember(packageId, storage)
        return storage
    }

    public async remove(packageId: string): Promise<void> {
        if (!isValidPackageId(packageId)) {
            return
        }
        this.storages.delete(packageId)
        const freed = await directorySize(this.packageDir(packageId))
        await rm(this.packageDir(packageId), {recursive: true, force: true})
        this.adjustUsage(-freed)
        log.info(`Removed libraries of package ${packageId} (${freed} bytes freed, ${this.usedBytes} in use).`)
    }

    public usage(): PackageLibraryCacheUsage {
        return {usedBytes: this.usedBytes, quotaBytes: this.quotaBytes, reconciling: this.measuring !== undefined}
    }

    public async awaitUsage(): Promise<void> {
        if (!this.measuring) {
            return
        }
        const nearlyFull = this.quotaBytes > 0 && this.usedBytes >= this.quotaBytes * NEARLY_FULL
        if (this.usageKnown && !nearlyFull) {
            return
        }
        await this.measuring
    }

    public async flush(): Promise<void> {
        if (this.saveTimer) {
            clearTimeout(this.saveTimer)
            this.saveTimer = undefined
        }
        if (this.dirty) {
            await this.saveUsage()
        }
    }

    /**
     * Measures the cache in the background and replaces the running total with the result. The walk
     * can overlap with imports and removals, which it may or may not have seen depending on where it
     * was; they are added on top, so at worst the total is off by those few packages until the next
     * start measures again.
     */
    private startMeasuring(): void {
        const started = Date.now()
        this.changeDuringMeasurement = 0
        this.measuring = directorySize(this.root, [STAGING_DIR, USAGE_FILE, USAGE_FILE_TMP])
            .then(total => {
                const assumed = this.usedBytes
                this.usedBytes = Math.max(0, total + this.changeDuringMeasurement)
                log.info(
                    `Measured the library cache in ${Date.now() - started} ms: ${this.usedBytes} bytes ` +
                    `(${this.usageKnown ? `remembered ${assumed}` : 'nothing remembered'}).`
                )
            })
            .catch(error => {
                log.error(`Could not measure the library cache, keeping ${this.usedBytes} bytes: ${error.message}`)
            })
            .then(() => {
                this.usageKnown = true
                this.measuring = undefined
                this.markDirty()
            })
    }

    private adjustUsage(delta: number): void {
        this.usedBytes = Math.max(0, this.usedBytes + delta)
        if (this.measuring) {
            this.changeDuringMeasurement += delta
        }
        this.markDirty()
    }

    private markDirty(): void {
        this.dirty = true
        if (!this.saveTimer) {
            this.saveTimer = setTimeout(() => {
                this.saveTimer = undefined
                void this.saveUsage()
            }, SAVE_DELAY_MS)
            // Never keep the process alive just for this; flush() runs on shutdown.
            this.saveTimer.unref()
        }
    }

    private async saveUsage(): Promise<void> {
        this.dirty = false
        try {
            const tmp = path.join(this.root, USAGE_FILE_TMP)
            await writeFile(tmp, JSON.stringify({usedBytes: this.usedBytes, writtenAt: new Date().toISOString()}))
            await rename(tmp, path.join(this.root, USAGE_FILE))
        } catch (error: any) {
            this.dirty = true
            log.warn(`Could not remember the library cache size: ${error.message}`)
        }
    }

    private async loadRememberedUsage(): Promise<number | undefined> {
        try {
            const parsed = JSON.parse(await readFile(path.join(this.root, USAGE_FILE), 'utf8'))
            return Number.isFinite(parsed?.usedBytes) && parsed.usedBytes >= 0 ? parsed.usedBytes : undefined
        } catch {
            // Missing on a first start or after an upgrade, or unreadable: measure instead.
            return undefined
        }
    }

    private remember(packageId: string, storage: ILibraryStorage): void {
        this.storages.set(packageId, storage)
        while (this.storages.size > this.maxCachedStorages) {
            this.storages.delete(this.storages.keys().next().value)
        }
    }

    private stagingRoot(): string {
        return path.join(this.root, STAGING_DIR)
    }

    private stagingDir(id: string): string {
        return path.join(this.stagingRoot(), id)
    }

    private packageDir(packageId: string): string {
        return path.join(this.root, packageId)
    }
}

/**
 * Disk space occupied by everything below `dir`, in bytes; `0` if it does not exist.
 *
 * Counts **allocated blocks**, not file sizes. The difference is not academic here: an H5P package is
 * thousands of small files (~1800 per package, averaging well under one block), so block rounding adds
 * roughly 45% on a 4K filesystem. Sizing the quota by file size would let the volume fill long before
 * the quota was reached.
 *
 * `blocks * 512` is what the storage itself reports on every backing we run on: it matches `du` on a
 * PVC, and on a memory-backed volume (`emptyDir: {medium: Memory}`, i.e. tmpfs) it matches `df`
 * exactly - which is what the volume's `sizeLimit` enforces and what counts against the pod's memory.
 * Directories are included for the same reason; tmpfs reports 0 blocks for them, a disk filesystem
 * does not.
 *
 * Note this would double-count hard-linked files. Nothing creates them today (packages are copied,
 * deliberately), but a future de-duplicating store would have to track inodes here.
 *
 * @param skipTopLevel names to ignore, but only directly inside `dir` (used to keep the staging
 * area out of the published total without hiding a package that happens to contain such a name)
 */
export const directorySize = async (dir: string, skipTopLevel: string[] = []): Promise<number> => {
    // The cache holds millions of small files, so awaiting every stat in turn makes the startup walk
    // take minutes: entries are statted concurrently, with a global cap on the filesystem calls in
    // flight so the libuv thread pool is kept busy without queueing an unbounded number of them.
    //
    // The cap bounds what *runs*, not what *exists*. Creating a task for every entry of a directory up
    // front (Promise.all over all of them) piles up one pending task per file of everything discovered
    // so far - the walk then proceeds breadth first and ends up holding the whole tree in memory. At
    // ~5 million files in production that exhausted the 4 GB heap (and took far longer than it needed
    // to, because the garbage collector was working on millions of live promises). Handling a
    // directory's entries in chunks keeps the walk depth first, and memory at a few thousand tasks.
    const limit = createLimiter(DIRECTORY_SIZE_CONCURRENCY)
    const walk = async (current: string, skip: string[]): Promise<number> => {
        let entries
        try {
            entries = await limit(() => readdir(current, {withFileTypes: true}))
        } catch {
            return 0
        }
        const wanted = skip.length ? entries.filter(entry => !skip.includes(entry.name)) : entries
        let total = 0
        for (let from = 0; from < wanted.length; from += DIRECTORY_SIZE_FANOUT) {
            const sizes = await Promise.all(wanted.slice(from, from + DIRECTORY_SIZE_FANOUT).map(async entry => {
                const full = path.join(current, entry.name)
                let size: number
                try {
                    size = (await limit(() => stat(full))).blocks * 512
                } catch {
                    // Raced with a delete - not worth failing a quota check over.
                    return 0
                }
                return entry.isDirectory() ? size + await walk(full, []) : size
            }))
            for (const size of sizes) {
                total += size
            }
        }
        return total
    }
    return walk(dir, skipTopLevel)
}

/** Filesystem calls of the cache walk in flight at once. */
const DIRECTORY_SIZE_CONCURRENCY = 64

/**
 * Entries of one directory handled at once. Applies per level, so a package directory, its libraries and
 * their files can be 16 x 16 x 16 tasks deep - plenty to keep the 64 filesystem calls above busy.
 */
const DIRECTORY_SIZE_FANOUT = 16

/** Runs at most `max` of the given async tasks at once; the rest wait their turn in FIFO order. */
const createLimiter = (max: number) => {
    let running = 0
    const waiting: Array<() => void> = []
    return async <T>(task: () => Promise<T>): Promise<T> => {
        if (running >= max) {
            await new Promise<void>(resolve => waiting.push(resolve))
        }
        running++
        try {
            return await task()
        } finally {
            running--
            waiting.shift()?.()
        }
    }
}
