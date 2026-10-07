import {AsyncLocalStorage} from 'async_hooks'

/**
 * Marks the asynchronous call tree of a package import.
 *
 * The S3 client is shared by the import and by every player request. Only the import fans out over
 * thousands of files at once, so only its requests are throttled (see s3Throttle.ts); player requests
 * must keep flowing while an import is running. The marker travels with the async context, so the
 * client middleware can tell the two apart without the storages having to pass anything along.
 */
const importScope = new AsyncLocalStorage<true>()

/** Runs `task` (and everything it awaits) inside the import scope. */
export const runInImportScope = <T>(task: () => Promise<T>): Promise<T> =>
    importScope.run(true, task)

/** Whether the caller runs inside {@link runInImportScope}. */
export const inImportScope = (): boolean => importScope.getStore() === true
