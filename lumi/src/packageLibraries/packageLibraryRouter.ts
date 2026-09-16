import express from 'express'
import path from 'path'
import {H5pError, LibraryName, Logger} from '@lumieducation/h5p-server'
import {PackageLibraryStore} from './types'
import {PACKAGE_LIBRARIES_SEGMENT} from './paths'

const log = new Logger('PackageLibraryRouter')

/**
 * Serves the library files of a package from its own isolated cache.
 *
 * The stock `h5pAjaxExpressRouter` route (`/libraries/:uberName/...`) always reads from the editor's
 * library storage, so a player running on a different storage needs its own route. This mirrors
 * `H5PAjaxEndpoint.getLibraryFile` minus the range handling - library files are never
 * range-requested - and keeps the stock route free to keep serving content imported before this
 * feature existed.
 *
 * Note there is no permission check here, matching the stock route: library files are the same
 * public code for everyone, and the content behind them is protected separately.
 */
const packageLibraryRouter = (store: PackageLibraryStore): express.Router => {
    const router = express.Router()

    // `:file(*)` is Express 4 wildcard syntax - Express 5's `*file` would not match here.
    router.get(`/${PACKAGE_LIBRARIES_SEGMENT}/:packageId/:uberName/:file(*)`, async (req, res) => {
        const {packageId, uberName} = req.params
        const filename = req.params.file
        try {
            const storage = await store.storageFor(packageId)
            if (!storage) {
                log.debug(`No library cache for package ${packageId}.`)
                res.status(404).end()
                return
            }
            const library = LibraryName.fromUberName(uberName)
            // Filenames are validated by the storage, which rejects absolute and traversing paths.
            const [stats, stream] = await Promise.all([
                storage.getFileStats(library, filename),
                storage.getFileStream(library, filename)
            ])

            res.type(path.extname(filename) || 'application/octet-stream')
            res.setHeader('Content-Length', stats.size)
            // Safe to cache hard: both the package id and the exact patch version are in the URL, so
            // a different package or a re-import never reuses this one.
            res.setHeader('Cache-Control', 'public, max-age=31536000')

            stream.on('error', (error: Error) => {
                log.error(`Error while streaming ${uberName}/${filename} of package ${packageId}: ${error.message}`)
                res.destroy(error)
            })
            stream.pipe(res)
        } catch (error: any) {
            const status = error instanceof H5pError ? error.httpStatusCode : 500
            log.debug(`Could not serve ${uberName}/${filename} of package ${packageId}: ${error.message}`)
            res.status(status || 500).end()
        }
    })

    return router
}

export default packageLibraryRouter
