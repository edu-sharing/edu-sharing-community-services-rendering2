/**
 * Extends a space separated extension whitelist of the H5P validator (`contentWhitelist`,
 * `libraryWhitelist`) with the extensions in `extra`. Additive on purpose: the defaults stay in force, so a typo
 * can never shrink the list and reject packages that import today. Extensions are case-insensitive, may be written
 * with a leading dot, and may be separated by spaces or commas.
 */
export function extendWhitelist(base: string, extra: string | undefined): string {
    const known = base.split(/\s+/).filter(Boolean)
    const seen = new Set(known.map(extension => extension.toLowerCase()))
    for (const raw of (extra ?? '').split(/[\s,]+/)) {
        const extension = raw.replace(/^\./, '').toLowerCase()
        if (/^[a-z0-9]+$/.test(extension) && !seen.has(extension)) {
            seen.add(extension)
            known.push(extension)
        }
    }
    return known.join(' ')
}
