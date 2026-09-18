import {IH5PConfig, ILibraryName, UrlGenerator} from '@lumieducation/h5p-server'
import {packageLibrariesPath} from './paths'

/**
 * What `UrlGenerator.libraryFile` is handed: a library name plus the patch version, used as the
 * cache buster. `IFullLibraryName` itself is not re-exported by the package's index.
 */
type FullLibraryName = ILibraryName & {patchVersion: number}

/**
 * Rewrites library asset URLs so a page loads the libraries of its own package.
 *
 * `H5PPlayer` turns every `preloadedJs`/`preloadedCss` entry into a URL through
 * `urlGenerator.libraryFile(...)`, so overriding that one function scopes all static assets of a
 * rendered page. Runtime-resolved paths are covered separately by `H5PIntegration.urlLibraries`
 * (see `packagePlayer.ts`).
 */
export default class PackageUrlGenerator extends UrlGenerator {
    constructor(config: IH5PConfig, packageId: string) {
        super(config)

        // `libraryFile` is an arrow PROPERTY on UrlGenerator, not a prototype method. A subclass
        // method of the same name would be shadowed by the instance property the base constructor
        // assigns, and would never run - so it has to be replaced on the instance, here.
        this.libraryFile = (library: FullLibraryName, file: string): string => {
            // Absolute URLs are passed through untouched, exactly as the base implementation does.
            if (file.startsWith('http://') || file.startsWith('https://') || file.startsWith('/')) {
                return file
            }
            return (
                `${this.baseUrl()}${packageLibrariesPath(packageId)}` +
                `/${library.machineName}-${library.majorVersion}.${library.minorVersion}/${file}` +
                `?version=${library.majorVersion}.${library.minorVersion}.${library.patchVersion}`
            )
        }
    }
}
