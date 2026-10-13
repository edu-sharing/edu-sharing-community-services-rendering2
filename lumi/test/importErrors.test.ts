import assert from 'node:assert/strict'
import {test} from 'node:test'
import {AggregateH5pError, H5pError} from '@lumieducation/h5p-server'
import {importErrorStatus} from '../src/importErrors'
import {ImportDeadlineError} from '../src/importQueue'

/** What PackageValidator throws: an aggregate with status 400 holding the individual findings. */
const validation = (...ids: string[]) => {
    const error = new AggregateH5pError('package-validation-failed', {}, 400, undefined, 'VALIDATION_FAILED')
    ids.forEach(id => error.addError(new H5pError(id, {})))
    return error
}

test('a package over a size limit is 413', () => {
    assert.equal(importErrorStatus(validation('total-size-too-large')), 413)
    assert.equal(importErrorStatus(validation('file-size-too-large')), 413)
    assert.equal(importErrorStatus(validation('not-in-whitelist', 'total-size-too-large')), 413)
})

test('any other validation finding is 422', () => {
    assert.equal(importErrorStatus(validation('not-in-whitelist')), 422)
    assert.equal(importErrorStatus(validation('invalid-library-name', 'api-version-unsupported')), 422)
})

test('errors keep the client error status the library chose, and fall back to 500 otherwise', () => {
    assert.equal(importErrorStatus(new H5pError('unable-to-unzip', {}, 400)), 400)
    assert.equal(importErrorStatus(new H5pError('mongo-s3-content-storage:filename-too-long', {}, 400)), 400)
    assert.equal(importErrorStatus(new H5pError('error-generating-unique-content-filename', {}, 500)), 500)
    assert.equal(importErrorStatus(new H5pError('import-package-no-id-assigned')), 500)
})

test('the deadline, a full disk and lock timeouts have their own statuses', () => {
    assert.equal(importErrorStatus(new ImportDeadlineError(1000)), 504)
    assert.equal(importErrorStatus(Object.assign(new Error('no space left on device'), {code: 'ENOSPC'})), 507)
    assert.equal(importErrorStatus(new Error('timeout')), 503)
    assert.equal(importErrorStatus(new Error('occupation-time-exceeded')), 503)
    assert.equal(importErrorStatus(new H5pError('server:install-library-lock-timeout', {})), 503)
})

test('anything unknown is 500', () => {
    assert.equal(importErrorStatus(new Error('boom')), 500)
    assert.equal(importErrorStatus('a string'), 500)
    assert.equal(importErrorStatus(undefined), 500)
})
