import * as H5P from '@lumieducation/h5p-server'
import {H5PDeps} from '../createH5PEditor'
import eduSharingPlayer from '../eduSharingPlayer'
import PackageUrlGenerator from './PackageUrlGenerator'
import {packageLibrariesPath} from './paths'

/**
 * Builds a player that renders one package against its own libraries.
 */
export const createScopedPlayer = (
    deps: H5PDeps,
    packageId: string,
    libraryStorage: H5P.ILibraryStorage
): H5P.H5PPlayer => {
    const integrationDefaults: Partial<H5P.IIntegration> = {
        // Static asset URLs are handled by PackageUrlGenerator, but H5P core also resolves library
        // paths at RUNTIME through H5P.getLibraryPath(), which uses H5PIntegration.urlLibraries when
        // it is set and falls back to `${url}/libraries` otherwise. Without this, a content type
        // that builds its own asset URLs would load them from the global store and escape the scope.
        urlLibraries: `${deps.config.baseUrl}${packageLibrariesPath(packageId)}`
    }

    const player = new H5P.H5PPlayer(
        libraryStorage,
        deps.contentStorage,
        deps.config,
        integrationDefaults as H5P.IIntegration,
        new PackageUrlGenerator(deps.config, packageId),
        deps.translationCallback,
        undefined,
        undefined
    )
    // The renderer lives on the instance, so every scoped player needs it set again.
    player.setRenderer(eduSharingPlayer)
    return player
}
