const UNIT_MULTIPLIERS: Record<string, number> = {
    "": 1,
    B: 1,
    KB: 1024,
    MB: 1024 ** 2,
    GB: 1024 ** 3,
    TB: 1024 ** 4,
};

/**
 * Parses a human-readable data size like `10GB`/`500Mb`, or a plain byte count with no suffix, into
 * a number of bytes. Units are binary (1KB = 1024 bytes) to match the `rendering2` service's own
 * config binding (Spring Boot's `DataSize`) — the same quota string means the same number of bytes
 * on both sides of the `GET /edusharing/buckets` contract. Unit suffixes are case-insensitive.
 *
 * @throws Error if `value` isn't a plain number optionally followed by one of B/KB/MB/GB/TB.
 */
export function parseDataSize(value: string): number {
    const match = /^\s*(\d+)\s*([a-zA-Z]{0,2})\s*$/.exec(value);
    if (!match) {
        throw new Error(`Invalid data size: "${value}"`);
    }
    const [, amount, rawUnit] = match;
    const unit = rawUnit.toUpperCase();
    const multiplier = UNIT_MULTIPLIERS[unit];
    if (multiplier === undefined) {
        throw new Error(`Invalid data size unit "${rawUnit}" in "${value}" (expected one of B, KB, MB, GB, TB)`);
    }
    return Number(amount) * multiplier;
}

/**
 * Reads a size setting such as `H5P_MAX_TOTAL_SIZE=2GB` (see {@link parseDataSize}). Unset or empty means
 * `fallback`; an unparsable value also means `fallback`, reported through `onInvalid`, so a typo can
 * never silently remove or shrink a limit.
 */
export function readDataSize(raw: string | undefined, fallback: number, onInvalid?: (error: Error) => void): number {
    if (!raw || raw.trim() === '') {
        return fallback;
    }
    try {
        return parseDataSize(raw);
    } catch (error) {
        onInvalid?.(error as Error);
        return fallback;
    }
}
