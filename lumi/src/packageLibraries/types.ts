import {ILibraryStorage} from '@lumieducation/h5p-server'

/**
 * A library store for a package that is being imported but does not have its final id yet.
 *
 * The id only becomes known after `saveOrUpdateContent`, but the libraries have to be installed
 * before it - so an import writes into a staging area first and publishes it under the package id
 * afterwards (see {@link PackageLibraryStore.promote}).
 */
export interface LibraryStagingArea {
    /** Opaque id of the staging area; only the store itself interprets it. */
    readonly id: string
    /** The (empty) storage the package's libraries must be installed into. */
    readonly storage: ILibraryStorage
}

/** How much of the cache is in use, and the (soft) limit it is measured against. */
export interface PackageLibraryCacheUsage {
    /** Sum of the allocated size of all published packages. Staging areas are not counted. */
    usedBytes: number
    /**
     * The configured limit in bytes; `0` means no limit. A soft limit: lumi only reports it, the rendering
     * service's CacheCleaner frees the cache when it is exceeded (as for the content bucket). It never
     * makes an import fail.
     */
    quotaBytes: number
    /**
     * `usedBytes` is a real figure - remembered from an earlier run or measured - and not just the 0 of a
     * first start that has not finished measuring yet.
     */
    known: boolean
    /** The cache is being measured right now. */
    reconciling: boolean
    /** The size is known and exceeds a configured quota. Only a signal - nothing is refused because of it. */
    overQuota: boolean
    /** When the cache was last measured completely (epoch ms); undefined until the first measurement ended. */
    measuredAt?: number
}

/**
 * Per-package library storage.
 *
 * Each imported H5P package gets its own isolated set of libraries, holding exactly the versions
 * (major, minor AND patch) the package shipped. This is the piece `@lumieducation/h5p-server` cannot
 * express on its own: it keys libraries by the ubername `machineName-major.minor` only, so a single
 * global store can hold just one patch version of a library at a time - importing a package with a
 * newer patch overwrites it for every other package, and one with an older patch is silently skipped.
 *
 * The interface is the seam the later S3-backed variant drops into: nothing outside this module
 * knows whether packages live on a disk or in a bucket.
 */
export interface PackageLibraryStore {
    /** Opens a staging area for an import that has not produced a package id yet. */
    createStaging(): Promise<LibraryStagingArea>

    /**
     * Publishes a staging area under its final package id, replacing any previous libraries of that
     * package. After this the staging area no longer exists.
     *
     * @returns the size in bytes of the published package's libraries
     */
    promote(staging: LibraryStagingArea, packageId: string): Promise<number>

    /** Throws away a staging area of an import that failed. Must not throw if it is already gone. */
    discard(staging: LibraryStagingArea): Promise<void>

    /**
     * The libraries of a package, or `undefined` if the package has none - which is the normal case
     * for content imported before this feature was enabled, and the signal to fall back to the
     * global library storage.
     */
    storageFor(packageId: string): Promise<ILibraryStorage | undefined>

    /** Deletes a package's libraries. Must not throw if there are none. */
    remove(packageId: string): Promise<void>

    /** Current size of the cache and its limit. */
    usage(): PackageLibraryCacheUsage

    /** Writes the remembered cache size to disk now. Call before the process exits. */
    flush(): Promise<void>
}
