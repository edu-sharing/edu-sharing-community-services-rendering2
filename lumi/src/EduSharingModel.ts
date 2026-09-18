import { ObjectId } from "mongodb";

/**
 * Where the libraries of an imported package live.
 *
 * Absent means the shared global library storage — every import before the per-package cache existed,
 * and every import made while it is switched off. `package` means the libraries live in that package's
 * own directory in the library cache, and are reachable **only** there: the global store never
 * received them, so if that directory is gone the content cannot be rendered at all (see
 * `isPackageLibrariesMissing` in router.ts, which turns that into a re-import instead of a blank page).
 */
export type LibraryScope = 'package'

/**
 * Represents a model for EduSharing with metadata about nodes and content.
 */
export default class EduSharingModel {
    constructor(
        public nodeId: string,
        public contentId: string,
        public libraryScope?: LibraryScope,
        public id?: ObjectId
    ) {}
}
