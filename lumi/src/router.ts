import express from 'express'
import * as H5P from '@lumieducation/h5p-server';
import {IRequestWithUser } from "@lumieducation/h5p-express";
import multer from "multer";
import {Collection} from "@lumieducation/h5p-mongos3/node_modules/mongodb"
import EduSharingModel from "./EduSharingModel";

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
            response.status(404).end();
        } else {
            response.send(JSON.stringify({ contentId: result.contentId }))
            response.status(200).end()
        }
    })

    router.get('/edusharing/contentid/:contentId', async (request, response) => {
        const result = await eduCollection.findOne({contentId: request.params.contentId})
        if (!result || !result.nodeId) {
            response.status(404).end();
        } else {
            response.send(JSON.stringify({ nodeId: result.nodeId }))
            response.status(200).end()
        }
    })

    router.post(
        '/edusharing', upload.single('file'), async (request: IRequestWithUser, response) => {
            if (!request.body.nodeId) {
                response.status(400).send('Malformed request').end();
                return;
            }
            const nodeId = request.body.nodeId

            try {
                const result = await h5pEditor.uploadPackage(
                    request.file.buffer,
                    request.user,
                    {onlyInstallLibraries: false}
                )

                const contentId = await h5pEditor.saveOrUpdateContent(
                    undefined,
                    result.parameters,
                    result.metadata,
                    result.metadata.preloadedDependencies.filter(x=> result.metadata.mainLibrary == x.machineName).map(x=>`${x.machineName} ${x.majorVersion}.${x.minorVersion}`).find(x=>true),
                    request.user
                    )
                const eduEntry = new EduSharingModel(nodeId, contentId)
                await eduCollection.insertOne(eduEntry)
                response.send(JSON.stringify({ contentId }));
                response.status(200).end();
            } catch (error) {
                response.status(500).end(error.message);
            }
        }
    )

    router.get(
        `/:contentId`,
        async (req: IRequestWithUser, res) => {
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
                res.status(200).end();
            } catch (error) {
                res.status(500).end(error.message);
            }
        }
    );

    return router
}

export default router