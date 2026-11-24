import * as H5P from '@lumieducation/h5p-server'
import {H5pError, Logger} from '@lumieducation/h5p-server'
import {promises as fsPromises} from 'fs'
import os from 'os'
import path from 'path'
import {TemporaryStorageMode} from './s3Scopes'

const log = new Logger('ImportTemporaryStorage')

const DIR_PREFIX = 'lumi-import-'

/** What an import needs to know about the temp storage; `H5PDeps` satisfies it. */
export interface TemporaryStorageSource {
    temporaryStorageMode: TemporaryStorageMode
    /** The shared storage; only used in `s3` mode. */
    temporaryStorage: H5P.ITemporaryFileStorage
}

const importRoot = (): string => process.env.H5P_UPLOAD_DIR || os.tmpdir()

/**
 * Runs `fn` with the temporary storage of ONE import.
 *
 * In `s3` mode that is the shared S3 storage, untouched. In `disk` mode it is a directory of its own,
 * removed when `fn` settles: h5p-server leaves the temp files of newly saved content behind (it deletes
 * them only on updates), and with a shared bucket there is no lifecycle rule to expire them. Call it
 * INSIDE the import queue's task, so an import that was abandoned at its deadline still cleans up when
 * it finally settles.
 *
 * The temp filename becomes the content file's S3 key (the content storer copies temp -> content under
 * the same name), hence the S3 character set instead of the disk default, which drops `@`.
 * Names are shortened to about 214 minus the directory length by the storage; nothing fails.
 */
export const withImportTemporaryStorage = async <T>(
    source: TemporaryStorageSource,
    fn: (temporaryStorage: H5P.ITemporaryFileStorage) => Promise<T>,
    root: string = importRoot()
): Promise<T> => {
    if (source.temporaryStorageMode !== 'disk') {
        return fn(source.temporaryStorage)
    }
    const directory = await fsPromises.mkdtemp(path.join(root, DIR_PREFIX))
    try {
        return await fn(new H5P.fsImplementations.DirectoryTemporaryFileStorage(directory, {
            invalidCharactersRegexp: /[^A-Za-z0-9\-._!()@/]/g
        }))
    } finally {
        await fsPromises.rm(directory, {recursive: true, force: true}).catch((error: any) =>
            log.warn(`Could not remove import temp directory ${directory}: ${error.message}`))
    }
}

/**
 * Removes import directories a crashed process left behind. Only safe before the server listens: the
 * volume belongs to this pod and no import can be running yet.
 */
export const sweepImportTemporaryDirs = async (root: string = importRoot()): Promise<number> => {
    let removed = 0
    let entries: string[]
    try {
        entries = await fsPromises.readdir(root)
    } catch (error: any) {
        log.warn(`Could not scan ${root} for stale import directories: ${error.message}`)
        return 0
    }
    for (const entry of entries.filter(name => name.startsWith(DIR_PREFIX))) {
        try {
            await fsPromises.rm(path.join(root, entry), {recursive: true, force: true})
            removed++
        } catch (error: any) {
            log.warn(`Could not remove stale import directory ${entry}: ${error.message}`)
        }
    }
    if (removed) {
        log.info(`Removed ${removed} stale import temp director${removed === 1 ? 'y' : 'ies'} from ${root}.`)
    }
    return removed
}

/**
 * The main editor's temp storage in `disk` mode. Temp files only exist during an import, in the
 * import's own storage; the routes that could reach this one (`/temp-files`, `POST /ajax`) are off.
 */
export class UnavailableTemporaryStorage implements H5P.ITemporaryFileStorage {
    private fail(): Promise<never> {
        // Rejected, not thrown, so callers that await or catch see it as with any other storage.
        return Promise.reject(new H5pError('lumi:temporary-storage-unavailable', {}, 500))
    }

    deleteFile(): Promise<void> {
        return this.fail()
    }

    fileExists(): Promise<boolean> {
        return this.fail()
    }

    getFileStats(): Promise<H5P.IFileStats> {
        return this.fail()
    }

    getFileStream(): Promise<any> {
        return this.fail()
    }

    listFiles(): Promise<H5P.ITemporaryFile[]> {
        return this.fail()
    }

    saveFile(): Promise<H5P.ITemporaryFile> {
        return this.fail()
    }
}
