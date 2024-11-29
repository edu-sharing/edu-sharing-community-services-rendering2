import express from 'express';
import bodyParser from 'body-parser';

import H5P, {fsImplementations, H5PConfig, H5PPlayer} from '@lumieducation/h5p-server';
import path from "path";
import i18next from "i18next";
import i18nextFsBackend from 'i18next-fs-backend';
import i18nextHttpMiddleware from 'i18next-http-middleware';
import createH5PEditor from "./createH5PEditor";
import {h5pAjaxExpressRouter, IRequestWithUser} from "@lumieducation/h5p-express";
import router from "./router";
import User from "./User";
import dotenv from 'dotenv';
import * as dbImplementations from '@lumieducation/h5p-mongos3';
import {h5p_core_version_major, h5p_core_version_minor, h5p_core_version_patch} from "./h5p.settings";


const start = async () => {
    console.log("Lumi Server started");
    dotenv.config();

    const translationFunction = await i18next
        .use(i18nextFsBackend)
        .use(i18nextHttpMiddleware.LanguageDetector)
        .init({
            backend: {
                loadPath: path.join(
                    __dirname,
                    '../node_modules/@lumieducation/h5p-server/build/assets/translations/{{ns}}/{{lng}}.json'
                )
            },
            debug: false,
            defaultNS: 'server',
            fallbackLng: 'en',
            ns: [
                'client',
                'copyright-semantics',
                'hub',
                'library-metadata',
                'metadata-semantics',
                'mongo-s3-content-storage',
                's3-temporary-storage',
                'server',
                'storage-file-implementations'
            ],
            preload: ['en', 'de'] // If you don't use a language detector of
            // i18next, you must preload all languages you want to use!
        });

    // Load the configuration file from the local file system
    const config = await new H5PConfig(
        new fsImplementations.JsonStorage(
            path.join(__dirname, '../config.json')
        )
    ).load();

    config.baseUrl = process.env.BASE_URL || config.baseUrl
    config.coreApiVersion = {
        major: h5p_core_version_minor,
        minor: h5p_core_version_major
    }
    config.h5pVersion = `${h5p_core_version_major}.${h5p_core_version_major}.${h5p_core_version_patch}`

    console.log("Config loaded:")
    console.log(config)

    // Init mongoDB
    const mongoDb = await dbImplementations.initMongo()
    console.log("MongoDB successfully initialized")

    const h5pEditor: H5P.H5PEditor = await createH5PEditor(
        config,
        mongoDb,
        (key, language) => translationFunction(key, { lng: language })
    );

    // The H5PPlayer object is used to display H5P content.
    const h5pPlayer = new H5PPlayer(
        h5pEditor.libraryStorage,
        h5pEditor.contentStorage,
        config,
        undefined,
        undefined,
        (key, language) => translationFunction(key, { lng: language }),
        undefined,
        h5pEditor.contentUserDataStorage
    );

    const server = express();

    server.use(express.json())
    server.use(bodyParser.urlencoded({ extended: true }));

    // A user is needed in every request. I For our purposes, a dummy is sufficient
    // (At least for the time being)
    server.use((req: IRequestWithUser, res, next) => {
        req.user = new User();
        next();
    });

    // The i18nextExpressMiddleware injects the function t(...) into the req
    // object. This function must be there for the Express adapter
    // (H5P.adapters.express) to function properly.
    server.use(i18nextHttpMiddleware.handle(i18next));

    server.use(
        h5pEditor.config.baseUrl,
        h5pAjaxExpressRouter(
            h5pEditor,
            path.resolve(path.join(__dirname, '../h5p/core')),
            path.resolve(path.join(__dirname, '../h5p/editor')),
            undefined,
            'auto' // You can change the language of the editor here by setting
            // the language code you need here. 'auto' means the route will try
            // to use the language detected by the i18next language detector.
        )
    );

    const eduCollection = mongoDb.collection(process.env.EDUSHARING_MONGO_COLLECTION)
    await eduCollection.createIndexes([{key: { 'nodeId': 1}}])

    server.use(
        h5pEditor.config.baseUrl,
        router(h5pEditor, h5pPlayer, 'auto', eduCollection),
    );

    const port = process.env.PORT || '3000';
    server.listen(port);
    console.log(`Server started successfully on port ${port}`)
}

start()
