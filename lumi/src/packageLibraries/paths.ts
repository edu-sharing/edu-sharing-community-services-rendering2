/**
 * The URL segment under which package-scoped library files are served.
 *
 * Deliberately NOT `libraries`: the stock `h5pAjaxExpressRouter` already owns
 * `${config.librariesUrl}/:uberName/:file(*)` (= `/libraries/...`) and serves it from the *global*
 * editor storage. Keeping a separate first segment lets both routes coexist, which is what makes
 * the fallback for content imported before this feature possible.
 */
export const PACKAGE_LIBRARIES_SEGMENT = 'package-libraries'

/**
 * Path of a package's library root, relative to `config.baseUrl`.
 *
 * Single source of truth for the three places that must agree on the shape: the URL generator that
 * writes the asset URLs into the page, the `H5PIntegration.urlLibraries` override that H5P core
 * uses at runtime, and the route that actually serves the files.
 */
export const packageLibrariesPath = (packageId: string): string =>
    `/${PACKAGE_LIBRARIES_SEGMENT}/${packageId}`
