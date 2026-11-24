/**
 * Puts one storage of lumi under a key prefix of a (shared) bucket.
 *
 * h5p-mongos3 addresses objects as `${contentId}/${file}` etc. and has no prefix setting. Rather than
 * fork it, the S3 client of that storage gets a middleware that prepends the prefix to every key the
 * library sends and strips it again from list results (the library cuts its *own* prefix length off
 * the returned keys).
 */

interface PrefixMiddlewareHolder {
    middlewareStack: {
        add(middleware: (next: (args: any) => Promise<any>, context: any) => (args: any) => Promise<any>,
            options: { step: 'initialize'; name: string }): void
    }
}

const stripPrefix = (value: unknown, prefix: string): unknown =>
    typeof value === 'string' && value.startsWith(prefix) ? value.slice(prefix.length) : value

/**
 * A prefixed copy of a command input. Never mutates `input`: h5p-mongos3 computes the Content-MD5 of
 * a batch delete from its own params beforehand, so they must stay as they were.
 */
export const prefixInput = (input: any, prefix: string, commandName?: string): any => {
    if (input?.CopySource !== undefined) {
        // A copy would read or write outside the prefix. h5p-mongos3 never copies; fail loudly if that changes.
        throw new Error(`S3 key prefix "${prefix}": ${commandName ?? 'a command'} with CopySource is not supported`)
    }
    const copy = {...input}
    if (typeof copy.Key === 'string') {
        copy.Key = prefix + copy.Key
    }
    if (commandName === 'ListObjectsV2Command') {
        // No Prefix means "everything"; with a key prefix that must still be only our own part.
        copy.Prefix = prefix + (typeof copy.Prefix === 'string' ? copy.Prefix : '')
    } else if (typeof copy.Prefix === 'string') {
        copy.Prefix = prefix + copy.Prefix
    }
    if (typeof copy.StartAfter === 'string') {
        copy.StartAfter = prefix + copy.StartAfter
    }
    if (Array.isArray(copy.Delete?.Objects)) {
        copy.Delete = {
            ...copy.Delete,
            Objects: copy.Delete.Objects.map((object: any) => ({...object, Key: prefix + object.Key}))
        }
    }
    return copy
}

/** Installs the prefixing on `client`. An empty prefix installs nothing, so the default is untouched. */
export const installS3KeyPrefix = (client: PrefixMiddlewareHolder, prefix: string): void => {
    if (!prefix) {
        return
    }
    client.middlewareStack.add(
        (next, context) => async args => {
            const commandName: string | undefined = context?.commandName
            const result = await next({...args, input: prefixInput(args.input, prefix, commandName)})
            if (commandName === 'ListObjectsV2Command' && result?.output) {
                const output = result.output
                if (Array.isArray(output.Contents)) {
                    output.Contents = output.Contents.map((entry: any) => ({...entry, Key: stripPrefix(entry.Key, prefix)}))
                }
                if (Array.isArray(output.CommonPrefixes)) {
                    output.CommonPrefixes = output.CommonPrefixes.map(
                        (entry: any) => ({...entry, Prefix: stripPrefix(entry.Prefix, prefix)}))
                }
                if (output.Prefix !== undefined) {
                    output.Prefix = stripPrefix(output.Prefix, prefix)
                }
            }
            return result
        },
        {step: 'initialize', name: 'lumiS3KeyPrefix'}
    )
}
