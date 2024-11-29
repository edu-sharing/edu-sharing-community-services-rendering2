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
            console.log(`Content ID for node ${request.params.nodeId} not found. It needs to be added.`)
            response.status(404).end();
        } else {
            console.log(`Content ID for node ${request.params.nodeId} found: ${result.contentId}.`)
            response.send(JSON.stringify({ contentId: result.contentId }))
            response.status(200).end()
        }
    })

    router.get('/edusharing/contentid/:contentId', async (request, response) => {
        const result = await eduCollection.findOne({contentId: request.params.contentId})
        if (!result || !result.nodeId) {
            console.log(`Node ID for content ${request.params.contentId} not found. It needs to be added.`)
            response.status(404).end();
        } else {
            console.log(`Node ID for content ${request.params.contentId} found: ${result.nodeId}.`)
            response.send(JSON.stringify({ nodeId: result.nodeId }))
            response.status(200).end()
        }
    })

    router.post(
        '/edusharing', upload.single('file'), async (request: IRequestWithUser, response) => {
            if (!request.body.nodeId) {
                console.log(`Missing param: nodeId. File upload aborted.`)
                response.status(400).send('Malformed request').end();
                return;
            }
            const nodeId = request.body.nodeId
            console.log(`Starting H5P package upload for nodeId ${request.body.nodeId}.`)

            try {
                const result = await h5pEditor.uploadPackage(
                    request.file.buffer,
                    request.user,
                    {onlyInstallLibraries: false}
                )

                console.log(`Valid H5P data imported for nodeId: ${request.body.nodeId}.`)

                const contentId = await h5pEditor.saveOrUpdateContent(
                    undefined,
                    result.parameters,
                    result.metadata,
                    result.metadata.preloadedDependencies.filter(x=> result.metadata.mainLibrary == x.machineName).map(x=>`${x.machineName} ${x.majorVersion}.${x.minorVersion}`).find(x=>true),
                    request.user
                    )
                console.log(`Lumi content Id for ES-Node ${request.body.nodeId} successfully created: ${contentId}.`)
                const eduEntry = new EduSharingModel(nodeId, contentId)
                await eduCollection.insertOne(eduEntry)
                response.send(JSON.stringify({ contentId }));
                console.log(`Lumi upload process for ES-Node ${request.body.nodeId} was successful.`)
                response.status(200).end();
            } catch (error) {
                console.log(`Lumi upload not successful. Error message: ${error.message}`)
                response.status(500).end(error.message);
            }
        }
    )

    router.get(
        `/:contentId`,
        async (req: IRequestWithUser, res) => {
            console.log(`Starting rendering of H5P Player for content ID ${req.params.contentId}`)
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
                console.log(`Rendering successful. Content Id: ${req.params.contentId}`)
                res.status(200).end();
            } catch (error) {
                console.log(`Rendering not successful. Content Id: ${req.params.contentId}. Error message: ${error.message}`)
                res.status(500).end(error.message);
            }
        }
    );

    return router
}

export default router