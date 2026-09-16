import * as H5P from '@lumieducation/h5p-server'
import {Logger} from '@lumieducation/h5p-server'
import {H5PDeps} from '../createH5PEditor'
import {CacheQuotaExceededError, PackageLibraryStore} from './types'

const log = new Logger('PackageImport')

/**
 * The ubername of a package's main library, in the whitespace-separated form
 * `saveOrUpdateContent` expects ("H5P.Foo 1.2", not "H5P.Foo-1.2").
 */
export const mainLibraryUbername = (metadata: H5P.IContentMetadata): string | undefined =>
    (metadata.preloadedDependencies || [])
        .filter((dependency) => metadata.mainLibrary === dependency.machineName)
        .map((dependency) => `${dependency.machineName} ${dependency.majorVersion}.${dependency.minorVersion}`)
        .find(() => true)

/**
 * Imports an H5P package into its own isolated library cache.
 *
 * The libraries have to be installed before `saveOrUpdateContent` can run, but the content id they
 * will be filed under only exists afterwards - so the import installs into a staging area and
 * publishes it under the new content id at the end.
 *
 * The isolation itself falls out of the scoped storage being empty: `LibraryManager` only compares
 * patch versions for a library that is already installed, so every library of the package installs
 * as new, at exactly the major.minor.patch the package shipped. Nothing is skipped for being older
 * than an installed copy, and nothing overwrites another package's libraries.
 *
 * Content and temporary files keep going to the shared storages, i.e. the same S3 buckets as before.
 *
 * @returns the new content id and the size of the libraries stored for it - the latter is what lets
 * the rendering service's CacheCleaner account for this package's share of the library volume.
 */
export const importPackage = async (
    deps: H5PDeps,
    store: PackageLibraryStore,
    data: Buffer,
    user: H5P.IUser
): Promise<{contentId: string; libraryBytes: number}> => {
    // Fail before doing any work if the cache is already full. `promote` re-checks with the
    // package's real size - only known once its libraries are installed - and is the authority.
    const {usedBytes, quotaBytes} = store.usage()
    if (quotaBytes > 0 && usedBytes >= quotaBytes) {
        throw new CacheQuotaExceededError(usedBytes, quotaBytes, 0)
    }

    const staging = await store.createStaging()
    try {
        const scopedEditor = new H5P.H5PEditor(
            deps.editorCache,
            deps.config,
            staging.storage,
            deps.contentStorage,
            deps.temporaryStorage,
            deps.translationCallback,
            undefined,
            {
                ...deps.editorOptions,
                // The install locks are keyed by ubername only. A private lock provider keeps this
                // import from contending with anything else over library names it does not share.
                lockProvider: new H5P.SimpleLockProvider()
            },
            undefined
        )

        const result = await scopedEditor.uploadPackage(data, user, {onlyInstallLibraries: false})
        log.debug(`Installed ${result.installedLibraries.length} libraries into staging area ${staging.id}.`)

        // Must run on the scoped editor too: storing content reads the libraries' semantics.
        const contentId = await scopedEditor.saveOrUpdateContent(
            undefined,
            result.parameters,
            result.metadata,
            mainLibraryUbername(result.metadata),
            user
        )

        const libraryBytes = await store.promote(staging, contentId)
        return {contentId, libraryBytes}
    } catch (error) {
        await store.discard(staging)
        throw error
    }
}
