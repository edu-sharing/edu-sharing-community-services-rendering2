import express from 'express'
import * as H5P from '@lumieducation/h5p-server';
import {IRequestWithUser } from "@lumieducation/h5p-express";
import multer from "multer";
import {Collection} from "@lumieducation/h5p-mongos3/node_modules/mongodb"
import EduSharingModel from "./EduSharingModel";
import {H5pError, Logger} from "@lumieducation/h5p-server";
import {parseDataSize} from "./dataSize";

const log = new Logger("Router")

/**
 * Serializes H5P package uploads.
 *
 * `h5pEditor.uploadPackage` extracts each package into its own temp directory and then
 * installs all contained libraries with an unbounded `Promise.all` (both across libraries
 * and across each library's files). Two uploads running at once therefore saturate S3; a
 * single library copy can then exceed the per-library lock's `installLibraryLockMaxOccupationTime`,
 * which rejects the whole `Promise.all`. `PackageImporter` reacts by `rm -rf`-ing the temp
 * directory in its `finally` block while sibling installs are still reading from it, producing
 * `ENOENT ... lstat '/tmp/tmp-...'` errors and — because `updateLibrary` deletes a library on
 * any error — a corrupted library store.
 *
 * Since lumi runs as a single replica with an in-memory `SimpleLockProvider`, serializing
 * uploads in-process removes the cross-upload race entirely and halves the S3 pressure.
 */
let uploadChain: Promise<unknown> = Promise.resolve()
const serializeUpload = <T>(task: () => Promise<T>): Promise<T> => {
    const run = uploadChain.then(task, task)
    // Keep the chain alive regardless of individual outcomes.
    uploadChain = run.then(() => undefined, () => undefined)
    return run
}

/**
 * Creates and configures an Express router for managing H5P content with EduSharing integration.
 *
 * This router provides endpoints for EduSharing functionality including retrieving and managing
 * H5P content (by nodeId or contentId), uploading packages, rendering H5P content, deleting content,
 * and checking service health.
 *
 * @param {H5P.H5PEditor} h5pEditor - H5P editor instance for managing content creation and updates.
 * @param {H5P.H5PPlayer} h5pPlayer - H5P player instance for rendering content.
 * @param {string | "auto"} [languageOverride="auto"] - Language override setting for the H5P player,
 *                                                      defaults to 'auto', which will use the language
 *                                                      in the request or fall back to English.
 * @param {Collection} eduCollection - MongoDB collection instance for managing EduSharing mapping of
 *                                      content and node IDs.
 *
 * @returns {express.Router} An Express router configured with endpoints for interacting with EduSharing
 *                           data and H5P functionality.
 *
 * Available Endpoints:
 * - GET `/edusharing/nodeid/:nodeId`: Fetches the contentId associated with a given nodeId.
 * - GET `/edusharing/contentid/:contentId`: Retrieves the nodeId associated with a given contentId.
 * - POST `/edusharing`: Uploads an H5P package for a given nodeId and links it to the contentId.
 * - GET `/:contentId`: Renders an H5P player page for the given contentId.
 * - DELETE `/edusharing/:nodeHash`: Deletes H5P content and its EduSharing mapping using a node's hash.
 * - GET `/edusharing/buckets`: Retrieves AWS S3 bucket configuration details.
 * - GET `/edusharing/ping`: Health check endpoint for ensuring the service is running.
 */
const router = (
    h5pEditor: H5P.H5PEditor,
    h5pPlayer: H5P.H5PPlayer,
    languageOverride: string | 'auto' = 'auto',
    eduCollection: Collection
): express.Router => {
    const router = express.Router()
    const upload = multer()

    router.get('/edusharing/nodeid/:nodeId', async (request, response) => {
        const result = await eduCollection.findOne({nodeId: request.params.nodeId})
        if (!result || !result.contentId) {
            log.info(`Content ID for node ${request.params.nodeId} not found. It needs to be added.`)
            response.status(404).end();
        } else {
            log.info(`Content ID for node ${request.params.nodeId} found: ${result.contentId}.`)
            response.send(JSON.stringify({ contentId: result.contentId }))
            response.status(200).end()
        }
    })

    router.get('/edusharing/contentid/:contentId', async (request, response) => {
        const result = await eduCollection.findOne({contentId: request.params.contentId})
        if (!result || !result.nodeId) {
            log.info(`Node ID for content ${request.params.contentId} not found. It needs to be added.`)
            response.status(404).end();
        } else {
            log.info(`Node ID for content ${request.params.contentId} found: ${result.nodeId}.`)
            response.send(JSON.stringify({ nodeId: result.nodeId }))
            response.status(200).end()
        }
    })

    router.post(
        '/edusharing', upload.single('file'), async (request: IRequestWithUser, response) => {
            if (!request.body.nodeId) {
                log.error(`Missing param: nodeId. File upload aborted.`)
                response.status(400).send('Malformed request').end();
                return;
            }
            const nodeId = request.body.nodeId
            log.info(`Starting H5P package upload for nodeId ${request.body.nodeId}.`)

            try {
                const result = await serializeUpload(() => h5pEditor.uploadPackage(
                    request.file.buffer,
                    request.user,
                    {onlyInstallLibraries: false}
                ))

                log.info(`Valid H5P data imported for nodeId: ${request.body.nodeId}.`)

                const contentId = await h5pEditor.saveOrUpdateContent(
                    undefined,
                    result.parameters,
                    result.metadata,
                    result.metadata.preloadedDependencies.filter(x=> result.metadata.mainLibrary == x.machineName).map(x=>`${x.machineName} ${x.majorVersion}.${x.minorVersion}`).find(x=>true),
                    request.user
                    )
                log.info(`Lumi content Id for ES-Node ${request.body.nodeId} successfully created: ${contentId}.`)
                const eduEntry = new EduSharingModel(nodeId, contentId)
                await eduCollection.insertOne(eduEntry)
                response.send(JSON.stringify({ contentId }));
                log.info(`Lumi upload process for ES-Node ${request.body.nodeId} was successful.`)
                response.status(200).end();
            } catch (error) {
                log.error(`Lumi upload not successful. Error message: ${error.message}`)
                response.status(500).end(error.message);
            }
        }
    )

    router.get(
        `/:contentId`,
        async (req: IRequestWithUser, res) => {
            log.info(`Starting rendering of H5P Player for content ID ${req.params.contentId}`)
            try {
                const h5pPage = await h5pPlayer.render(
                    req.params.contentId,
                    req.user,
                    languageOverride === 'auto'
                        ? req.language ?? 'en'
                        : languageOverride,
                    {
                        showCopyButton: false,
                        showDownloadButton: false,
                        showFrame: true,
                        showH5PIcon: true,
                        showLicenseButton: true,
                        contextId:
                            typeof req.query.contextId === 'string'
                                ? req.query.contextId
                                : undefined,
                        asUserId:
                            typeof req.query.asUserId === 'string'
                                ? req.query.asUserId
                                : undefined,
                        readOnlyState:
                            typeof req.query.readOnlyState === 'string'
                                ? req.query.readOnlyState === 'yes'
                                : undefined
                    }
                );
                res.send(h5pPage);
                log.info(`Rendering successful. Content Id: ${req.params.contentId}`)
                res.status(200).end();
            } catch (error) {
                log.error(`Rendering not successful. Content Id: ${req.params.contentId}. Error message: ${error.message}`)
                res.status(500).end(error.message);
            }
        }
    );

    router.delete("/edusharing/:nodeHash", async (request: IRequestWithUser, response) => {
        const result = await eduCollection.findOne({nodeId: request.params.nodeHash})
        if (!result || !result.contentId) {
            log.warn(`Requested node for deletion not found: ${request.params.nodeHash}`)
            response.status(404).end()
            return
        }
        try {
            await h5pEditor.deleteContent(result.contentId, request.user)
            await eduCollection.deleteOne({nodeId: request.params.nodeHash})
            response.status(204).end()
        } catch (error: any) {
            if (error instanceof H5pError) {
                log.warn(`Deletion failed with H5PError: ${error.message}`)
                response.status(error.httpStatusCode).end()
            } else {
                response.status(500).end()
            }
        }
    })

    router.get("/edusharing/buckets", async (_req, res) => {
        // Quota for the content bucket, enforced by rendering2's CacheCleaner. A plain byte count or
        // a human-readable size (e.g. "10GB", see parseDataSize); unset/empty => no limit. Kept
        // alongside the bucket name so both live in the same place instead of being duplicated into
        // rendering2's own repository registration. A misconfigured value must not break this
        // endpoint — it's polled by every admin dashboard tick and by the daily CacheCleaner.
        let contentBucketQuota: number | undefined
        if (process.env.CONTENT_AWS_S3_BUCKET_QUOTA) {
            try {
                contentBucketQuota = parseDataSize(process.env.CONTENT_AWS_S3_BUCKET_QUOTA)
            } catch (error) {
                log.warn(`Ignoring invalid CONTENT_AWS_S3_BUCKET_QUOTA: ${(error as Error).message}`)
            }
        }
        const buckets = {
            contentBucket: process.env.CONTENT_AWS_S3_BUCKET,
            contentBucketQuota
        }
        res.send(JSON.stringify(buckets))
        res.status(200).end()
    })

    router.get("/edusharing/ping", async (_req, res) => {
        res.status(200).end()
    })

    return router
}

export default router
