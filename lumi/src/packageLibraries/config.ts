import path from 'path'
import {Logger} from '@lumieducation/h5p-server'
import {parseDataSize} from '../dataSize'

const log = new Logger('PackageLibraryConfig')

/** How libraries of imported H5P packages are stored. */
export type LibraryCacheMode =
    /** One global store shared by every package - the behaviour this service has always had. */
    | 'global'
    /** One isolated store per imported package, holding its exact library versions. */
    | 'package'

export interface PackageLibraryConfig {
    mode: LibraryCacheMode
    /** Root directory of the per-package caches; only meaningful in `package` mode. */
    directory: string
    /** Size limit of the cache in bytes; `0` means no limit. */
    quotaBytes: number
}

const DEFAULT_DIRECTORY = '/application/library-cache'

/**
 * Reads the per-package library cache configuration from the environment.
 *
 * Defaults to `global`, so an existing deployment keeps its current behaviour until it opts in.
 */
export const readPackageLibraryConfig = (env: NodeJS.ProcessEnv = process.env): PackageLibraryConfig => ({
    mode: env.H5P_LIBRARY_CACHE === 'package' ? 'package' : 'global',
    directory: path.resolve(env.H5P_LIBRARY_CACHE_DIR || DEFAULT_DIRECTORY),
    quotaBytes: readQuota(env.H5P_LIBRARY_CACHE_QUOTA)
})

/**
 * A plain byte count or a human-readable size (e.g. "10GB"), in the same binary units as the bucket
 * quotas so one quota string means the same thing everywhere. Unset/empty/0 = no limit.
 *
 * A bad value must not stop the server from starting - it is logged and treated as "no limit",
 * matching how `CONTENT_AWS_S3_BUCKET_QUOTA` is handled on the buckets endpoint.
 */
const readQuota = (raw?: string): number => {
    if (!raw) {
        return 0
    }
    try {
        return parseDataSize(raw)
    } catch (error) {
        log.warn(`Ignoring invalid H5P_LIBRARY_CACHE_QUOTA: ${(error as Error).message}`)
        return 0
    }
}
