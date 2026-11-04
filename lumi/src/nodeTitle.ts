import {IContentMetadata, IUser} from "@lumieducation/h5p-server";

/**
 * Request header in which the rendering service sends the current title of the edu-sharing node on a player request.
 *
 * The repository never writes a changed title into the package, so the title in h5p.json is the one the package
 * was created with - often just the file name. Content types show it to the learner (Interactive Book on its cover
 * and above the table of contents), so the player shows the node's title instead.
 *
 * Percent-encoded (`encodeURIComponent` style), as header values cannot carry umlauts reliably. The rendering
 * service never forwards a header of this name from the browser (`LumiProxyService.FORWARDED_REQUEST_HEADERS`),
 * so it only ever holds the title the repository delivered.
 */
export const NODE_TITLE_HEADER = 'x-edu-sharing-node-title'

/**
 * The node title carried by [NODE_TITLE_HEADER], or `undefined` if there is none (a request that did not go through
 * the rendering service, or a node without a title) or it cannot be decoded.
 */
export const nodeTitleFromHeader = (value: string | string[] | undefined): string | undefined => {
    if (typeof value !== 'string') {
        return undefined
    }
    let title: string
    try {
        title = decodeURIComponent(value).trim()
    } catch {
        // URIError: not valid percent-encoding. Better the package's own title than a garbled one.
        return undefined
    }
    return title || undefined
}

/**
 * The metadata (h5p.json) of the content with its title replaced by the node title, for the `metadataOverride` of
 * `H5PPlayer.render`; `undefined` without a node title, so the player loads the metadata itself as before.
 *
 * The override replaces the whole metadata - the player then skips loading it - so it is loaded here and only the
 * title is changed. If loading fails, the player is left to load it again and report the failure the way it always did.
 */
export const metadataWithNodeTitle = async (
    contentManager: {getContentMetadata(contentId: string, user: IUser): Promise<IContentMetadata>},
    contentId: string,
    user: IUser,
    nodeTitle: string | undefined
): Promise<IContentMetadata | undefined> => {
    if (!nodeTitle) {
        return undefined
    }
    let metadata: IContentMetadata
    try {
        metadata = await contentManager.getContentMetadata(contentId, user)
    } catch {
        return undefined
    }
    return {...metadata, title: nodeTitle}
}
