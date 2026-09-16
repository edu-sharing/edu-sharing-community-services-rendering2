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

/** How much of the cache is in use, and the limit it is measured against. */
export interface PackageLibraryCacheUsage {
    /** Sum of the file sizes of all published packages. Staging areas are not counted. */
    usedBytes: number
    /** The configured limit in bytes; `0` means no limit. */
    quotaBytes: number
}

/**
 * Thrown when publishing a package would push the cache over its quota.
 *
 * The cache is the authoritative store for the libraries of the packages in it - evicting one would
 * silently break the content that uses it - so a full cache rejects new imports rather than making
 * room by deleting somebody else's libraries.
 */
export class CacheQuotaExceededError extends Error {
    constructor(
        public readonly usedBytes: number,
        public readonly quotaBytes: number,
        public readonly requiredBytes: number
    ) {
        super(
            `H5P library cache is full: ${usedBytes} bytes used of ${quotaBytes}, ` +
            `another ${requiredBytes} bytes required. Free space by deleting H5P content, ` +
            `or raise H5P_LIBRARY_CACHE_QUOTA.`
        )
        this.name = 'CacheQuotaExceededError'
    }
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
     * @throws CacheQuotaExceededError if publishing would exceed the cache's quota; the staging area
     * is left for the caller to {@link discard}.
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
}
