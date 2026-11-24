/**
 * Where lumi keeps its S3 data, and the rules that make a shared bucket safe.
 *
 * By default lumi uses three buckets of its own. A self-hosting operator who only has one bucket can
 * point them at the same one, which is only safe with key prefixes for content and libraries, disk
 * temp storage and no bucket management (the temp bucket's lifecycle rule would otherwise expire the
 * whole shared bucket). Everything here is pure, so the rules are testable without S3.
 */

export type TemporaryStorageMode = 's3' | 'disk'

export interface S3Scopes {
    contentBucket?: string
    libraryBucket?: string
    temporaryBucket?: string
    /** Normalized: empty, or without a leading and with exactly one trailing slash. */
    contentKeyPrefix: string
    libraryKeyPrefix: string
    manageBuckets: boolean
    temporaryStorage: TemporaryStorageMode
}

/**
 * Same normalization as the rendering service's `ExternalBucket.normalizeKeyPrefix`, so the same value
 * means the same thing on both sides: trimmed, no leading or trailing slashes, then exactly one
 * trailing slash - or empty when nothing is left.
 */
export const normalizeKeyPrefix = (raw: string | undefined | null): string => {
    const trimmed = (raw ?? '').trim().replace(/^\/+/, '').replace(/\/+$/, '')
    return trimmed ? `${trimmed}/` : ''
}

const readBoolean = (raw: string | undefined, fallback: boolean): boolean => {
    const value = (raw ?? '').trim().toLowerCase()
    if (value === 'true') {
        return true
    }
    if (value === 'false') {
        return false
    }
    return fallback
}

export const readS3Scopes = (env: NodeJS.ProcessEnv = process.env): S3Scopes => {
    const mode = (env.H5P_TEMPORARY_STORAGE ?? '').trim().toLowerCase()
    return {
        contentBucket: env.CONTENT_AWS_S3_BUCKET,
        libraryBucket: env.LIBRARY_AWS_S3_BUCKET,
        temporaryBucket: env.TEMPORARY_AWS_S3_BUCKET,
        contentKeyPrefix: normalizeKeyPrefix(env.CONTENT_AWS_S3_KEY_PREFIX),
        libraryKeyPrefix: normalizeKeyPrefix(env.LIBRARY_AWS_S3_KEY_PREFIX),
        manageBuckets: readBoolean(env.AWS_S3_MANAGE_BUCKETS, true),
        temporaryStorage: mode === 'disk' ? 'disk' : 's3'
    }
}

export interface S3ScopeFindings {
    /** The configuration is dangerous; lumi must not start. */
    fatal: string[]
    warnings: string[]
}

/** Two normalized prefixes overlap when one contains the other (an empty one contains everything). */
const prefixesOverlap = (a: string, b: string): boolean => a.startsWith(b) || b.startsWith(a)

export const validateS3Scopes = (scopes: S3Scopes): S3ScopeFindings => {
    const fatal: string[] = []
    const warnings: string[] = []

    if (scopes.temporaryStorage === 's3' && scopes.temporaryBucket) {
        const shared = [
            scopes.temporaryBucket === scopes.contentBucket ? 'CONTENT_AWS_S3_BUCKET' : undefined,
            scopes.temporaryBucket === scopes.libraryBucket ? 'LIBRARY_AWS_S3_BUCKET' : undefined
        ].filter(Boolean)
        if (shared.length) {
            const what = `TEMPORARY_AWS_S3_BUCKET "${scopes.temporaryBucket}" is the same bucket as ${shared.join(' and ')}`
            if (scopes.manageBuckets) {
                fatal.push(`${what}: the temporary bucket's lifecycle rule would expire everything in it. ` +
                    `Use H5P_TEMPORARY_STORAGE=disk, or AWS_S3_MANAGE_BUCKETS=false.`)
            } else {
                warnings.push(`${what}: temporary files are never expired there. Use H5P_TEMPORARY_STORAGE=disk.`)
            }
        }
    }

    if (scopes.contentBucket && scopes.contentBucket === scopes.libraryBucket
        && prefixesOverlap(scopes.contentKeyPrefix, scopes.libraryKeyPrefix)) {
        warnings.push(`Content and library storage share bucket "${scopes.contentBucket}" with overlapping key ` +
            `prefixes ("${scopes.contentKeyPrefix}" and "${scopes.libraryKeyPrefix}"). Give them distinct, ` +
            `non-nested CONTENT_AWS_S3_KEY_PREFIX / LIBRARY_AWS_S3_KEY_PREFIX values.`)
    }
    return {fatal, warnings}
}

/** Collections the rendering service owns; lumi must never be configured onto one of them. */
export const SERVICE_COLLECTIONS = [
    'renderingJob', 'subJob', 'Tracking', 'rendererKeyConfig', 'repositoryRegistration', 'shedLock'
]

/**
 * Lumi may share the rendering service's database. A wrong collection name would then write H5P
 * documents into a service collection, so this refuses to start instead.
 * @returns the problems found; empty when the names are fine.
 */
export const validateCollectionNames = (names: Record<string, string | undefined>): string[] => {
    const problems: string[] = []
    const entries = Object.entries(names).filter((entry): entry is [string, string] => !!entry[1])
    for (const [variable, name] of entries) {
        if (SERVICE_COLLECTIONS.includes(name)) {
            problems.push(`${variable}="${name}" is a collection of the rendering service.`)
        }
    }
    for (let i = 0; i < entries.length; i++) {
        for (let j = i + 1; j < entries.length; j++) {
            if (entries[i][1] === entries[j][1]) {
                problems.push(`${entries[i][0]} and ${entries[j][0]} use the same collection "${entries[i][1]}".`)
            }
        }
    }
    return problems
}

export interface S3SetupPlan {
    /** Buckets to create when missing, in the order they are ensured (empty without bucket management). */
    ensureBuckets: (string | undefined)[]
    /** Buckets that are only probed with HEAD, never created (the operator owns them). */
    headOnlyBuckets: (string | undefined)[]
    /** Whether the temp bucket gets its expiration rule. */
    setTemporaryLifecycle: boolean
    temporaryStorage: TemporaryStorageMode
    /** Empty means the storage shares the plain, unprefixed client (today's behaviour). */
    contentClientPrefix: string
    libraryClientPrefix: string
}

/**
 * The startup decisions about buckets and clients, kept pure so that "without the new settings nothing
 * changes" can be tested without S3. The order (library, content, temporary) is the order they have
 * always been ensured in. With disk temp storage there is no temp bucket to ensure, probe or expire; without
 * bucket management nothing is created and no lifecycle rule is written, because the buckets are not ours.
 */
export const planS3Setup = (scopes: S3Scopes): S3SetupPlan => {
    const buckets = [
        scopes.libraryBucket,
        scopes.contentBucket,
        ...(scopes.temporaryStorage === 'disk' ? [] : [scopes.temporaryBucket])
    ]
    return {
        ensureBuckets: scopes.manageBuckets ? buckets : [],
        headOnlyBuckets: scopes.manageBuckets ? [] : buckets,
        setTemporaryLifecycle: scopes.manageBuckets && scopes.temporaryStorage === 's3',
        temporaryStorage: scopes.temporaryStorage,
        contentClientPrefix: scopes.contentKeyPrefix,
        libraryClientPrefix: scopes.libraryKeyPrefix
    }
}
