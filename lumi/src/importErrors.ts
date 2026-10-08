import {H5pError} from '@lumieducation/h5p-server'
import {ImportDeadlineError} from './importQueue'

/**
 * The HTTP status a failed package import is answered with.
 *
 * Everything used to come back as 500, although most failures are the package's fault and no retry will
 * change them - the rendering service cannot tell those from a lumi that is broken. Now:
 *
 * | Status | Meaning |
 * |---|---|
 * | 400 | not a zip (`unable-to-unzip`), or whatever status the library itself chose (e.g. a key that is too long) |
 * | 413 | a size limit of the package was exceeded (`total-size-too-large`, `file-size-too-large`) |
 * | 422 | the package is not a valid H5P package (extension not allowed, broken `h5p.json`, ...) |
 * | 503 | the import failed on a lock timeout while installing libraries: temporary, worth retrying |
 * | 504 | the import did not finish within its deadline |
 * | 507 | the disk is full (`ENOSPC`) |
 * | 500 | anything else |
 */
export const importErrorStatus = (error: unknown): number => {
    if (error instanceof ImportDeadlineError) {
        return 504
    }
    const e = error as {code?: unknown; message?: unknown; errorId?: unknown; httpStatusCode?: unknown; errors?: unknown}
    if (e?.code === 'ENOSPC') {
        return 507
    }
    // SimpleLockProvider rejects with plain errors, the library installer with an H5pError of its own.
    if (e?.message === 'timeout' || e?.message === 'occupation-time-exceeded'
        || e?.errorId === 'server:install-library-lock-timeout') {
        return 503
    }
    if (error instanceof H5pError) {
        // The validator collects its findings in an aggregate error with the status 400.
        const findings = Array.isArray(e.errors) ? (e.errors as Array<{errorId?: unknown}>) : []
        if (findings.length > 0) {
            return findings.some(finding => typeof finding.errorId === 'string' && finding.errorId.endsWith('-size-too-large'))
                ? 413
                : 422
        }
        const status = Number(e.httpStatusCode)
        return status >= 400 && status < 500 ? status : 500
    }
    return 500
}
