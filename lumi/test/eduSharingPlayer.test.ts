import assert from 'node:assert/strict'
import {test} from 'node:test'
import {IPlayerModel} from '@lumieducation/h5p-server'
import eduSharingPlayer, {toScriptJson} from '../src/eduSharingPlayer'

const BREAKOUT = '</script><script>alert(1)</script><!--'

const model = (title: string) => ({
    contentId: 'c1',
    styles: [],
    scripts: [],
    integration: {
        url: '/public/h5p',
        saveFreq: false,
        contents: {
            'cid-c1': {
                library: 'H5P.InteractiveBook 1.11',
                jsonContent: '{}',
                metadata: {title}
            }
        }
    }
}) as unknown as IPlayerModel

test('a title cannot end the script element that carries the integration', () => {
    const page = eduSharingPlayer(model(BREAKOUT))

    assert.ok(!page.includes(BREAKOUT))
    // Only the player's own script elements are closed.
    assert.equal(page.split('</script>').length - 1, 2)
})

test('the integration still reads back with the title unchanged', () => {
    const page = eduSharingPlayer(model(BREAKOUT))

    const json = page.match(/window\.H5PIntegration = ([\s\S]*?);\n\s*<\/script>/)![1]
    assert.equal(JSON.parse(json).contents['cid-c1'].metadata.title, BREAKOUT)
})

test('toScriptJson escapes every < and otherwise matches JSON.stringify', () => {
    const value = {a: '<b>x</b>', c: ['<!--', 1, null], d: 'plain'}

    const json = toScriptJson(value, 2)

    assert.ok(!json.includes('<'))
    assert.deepEqual(JSON.parse(json), value)
    assert.equal(toScriptJson({d: 'plain'}, 2), JSON.stringify({d: 'plain'}, null, 2))
})
