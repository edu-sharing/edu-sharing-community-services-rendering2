import {access, mkdir, readdir, rename, rm, stat} from 'fs/promises'
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

    /** Cheap LRU of storages, most recently used last (`Map` preserves insertion order). */
    private readonly storages = new Map<string, ILibraryStorage>()

    /**
     * Prepares the cache root. Any staging directory found here is a leftover of an import that died
     * mid-flight - it can never be referenced again, so it is swept on startup.
     */
    public static async create(
        root: string,
        options: {quotaBytes?: number; maxCachedStorages?: number} = {}
    ): Promise<FsPackageLibraryStore> {
        const store = new FsPackageLibraryStore(root, options.maxCachedStorages ?? 256, options.quotaBytes ?? 0)
        await mkdir(root, {recursive: true})
        await rm(store.stagingRoot(), {recursive: true, force: true})
        await mkdir(store.stagingRoot(), {recursive: true})
        // The only full walk of the cache: from here on the total is maintained incrementally.
        store.usedBytes = await directorySize(root, [STAGING_DIR])
        log.info(
            `Per-package library cache ready at ${root} ` +
            `(${store.usedBytes} bytes used${store.quotaBytes ? ` of ${store.quotaBytes}` : ', no quota'}).`
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
        this.usedBytes = this.usedBytes - replaced + incoming
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
        this.usedBytes = Math.max(0, this.usedBytes - freed)
        log.info(`Removed libraries of package ${packageId} (${freed} bytes freed, ${this.usedBytes} in use).`)
    }

    public usage(): PackageLibraryCacheUsage {
        return {usedBytes: this.usedBytes, quotaBytes: this.quotaBytes}
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
const directorySize = async (dir: string, skipTopLevel: string[] = []): Promise<number> => {
    let entries
    try {
        entries = await readdir(dir, {withFileTypes: true})
    } catch {
        return 0
    }
    let total = 0
    for (const entry of entries) {
        if (skipTopLevel.includes(entry.name)) {
            continue
        }
        const full = path.join(dir, entry.name)
        try {
            total += (await stat(full)).blocks * 512
        } catch {
            // Raced with a delete - not worth failing a quota check over.
            continue
        }
        if (entry.isDirectory()) {
            total += await directorySize(full)
        }
    }
    return total
}
