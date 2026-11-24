import * as H5P from '@lumieducation/h5p-server'
import {H5PDeps} from './createH5PEditor'

/**
 * An editor for ONE import: the shared content storage and settings, but its own library and temporary
 * storage. Used by the per-package library cache (its staging library storage) and, with disk temp
 * storage, by global imports (the shared library storage, a temp directory per import).
 *
 * `lockProvider` is a parameter because the two callers differ: a package import must not contend with
 * anything over library names it does not share, a global import must use the shared lock.
 */
export const createImportEditor = (
    deps: H5PDeps,
    libraryStorage: H5P.ILibraryStorage,
    temporaryStorage: H5P.ITemporaryFileStorage,
    lockProvider: H5P.ILockProvider
): H5P.H5PEditor => new H5P.H5PEditor(
    deps.editorCache,
    deps.config,
    libraryStorage,
    deps.contentStorage,
    temporaryStorage,
    deps.translationCallback,
    undefined,
    {...deps.editorOptions, lockProvider},
    undefined
)
