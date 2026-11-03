import assert from 'node:assert/strict'
import {test} from 'node:test'
import {IContentMetadata, IUser} from '@lumieducation/h5p-server'
import {metadataWithNodeTitle, nodeTitleFromHeader} from '../src/nodeTitle'

const user = {id: 'user'} as IUser

const packageMetadata = {
    title: 'Stoffwechsel_Endokrinologie.h5p',
    mainLibrary: 'H5P.InteractiveBook',
    language: 'de',
    defaultLanguage: 'de',
    license: 'CC BY',
    embedTypes: ['iframe'],
    preloadedDependencies: [{machineName: 'H5P.InteractiveBook', majorVersion: 1, minorVersion: 11}]
} as IContentMetadata

/** A content manager that serves [packageMetadata] and records which content ids it was asked for. */
const contentManager = (fail = false) => {
    const requested: string[] = []
    return {
        requested,
        getContentMetadata: async (contentId: string) => {
            requested.push(contentId)
            if (fail) {
                throw new Error('content missing')
            }
            return packageMetadata
        }
    }
}

test('the header is percent-decoded, so umlauts and reserved characters survive', () => {
    const title = 'Pharmakologie – Endokrinologie & Stoffwechsel: Übersicht 100%'
    assert.equal(nodeTitleFromHeader(encodeURIComponent(title)), title)
})

test('a missing, blank or undecodable header yields no title', () => {
    assert.equal(nodeTitleFromHeader(undefined), undefined)
    assert.equal(nodeTitleFromHeader(''), undefined)
    assert.equal(nodeTitleFromHeader('%20%20'), undefined)
    assert.equal(nodeTitleFromHeader('%E0%A4%A'), undefined)
    assert.equal(nodeTitleFromHeader(['a', 'b']), undefined)
})

test('the node title replaces only the title of the package metadata', async () => {
    const manager = contentManager()

    const metadata = await metadataWithNodeTitle(manager, 'c1', user, 'Pharmakologie')

    assert.deepEqual(metadata, {...packageMetadata, title: 'Pharmakologie'})
    assert.deepEqual(manager.requested, ['c1'])
    // The stored metadata itself is left alone.
    assert.equal(packageMetadata.title, 'Stoffwechsel_Endokrinologie.h5p')
})

test('without a node title there is no override and nothing is loaded', async () => {
    const manager = contentManager()

    assert.equal(await metadataWithNodeTitle(manager, 'c1', user, undefined), undefined)
    assert.deepEqual(manager.requested, [])
})

test('if the metadata cannot be loaded there is no override, so the player reports the failure as before', async () => {
    assert.equal(await metadataWithNodeTitle(contentManager(true), 'c1', user, 'Pharmakologie'), undefined)
})
