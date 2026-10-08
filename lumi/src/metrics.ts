import * as promClient from 'prom-client'
import {getImportQueueStatus} from './importQueue'
import {getS3PoolStatus} from './s3Pool'
import {PackageLibraryStore} from './packageLibraries'

/**
 * Prometheus gauges for the two things that can silently stop every package import: a stuck import
 * and an exhausted S3 pool. Registered in the default registry, which express-prom-bundle serves.
 * Values are read when Prometheus scrapes, so nothing has to be kept in sync.
 */
export const registerImportMetrics = (libraryStore?: PackageLibraryStore): void => {
    const gauge = (name: string, help: string, read: () => number) =>
        new promClient.Gauge({
            name, help,
            collect() {
                this.set(read())
            }
        })

    gauge('lumi_import_waiting', 'Package imports waiting for their turn.',
        () => getImportQueueStatus()?.waiting ?? 0)
    gauge('lumi_import_running_seconds', 'How long the current package import has been running (0 if idle).',
        () => (getImportQueueStatus()?.runningForMs ?? 0) / 1000)
    gauge('lumi_import_deadline_exceeded', 'Package imports given up on since start.',
        () => getImportQueueStatus()?.deadlineExceeded ?? 0)
    gauge('lumi_s3_pool_in_use', 'S3 sockets currently checked out of the connection pool.',
        () => getS3PoolStatus()?.inUse ?? 0)
    gauge('lumi_s3_pool_queued', 'S3 requests waiting for a free socket.',
        () => getS3PoolStatus()?.queued ?? 0)
    gauge('lumi_s3_pool_stalled_seconds', 'How long the S3 pool has been full without any request settling.',
        () => (getS3PoolStatus()?.stalledForMs ?? 0) / 1000)
    gauge('lumi_s3_pool_recoveries', 'Times the watchdog destroyed the S3 pool sockets since start.',
        () => getS3PoolStatus()?.recoveries ?? 0)

    // The library cache quota is soft: lumi never refuses an import for it, so these are what an alert has to
    // be built on. Alert on `lumi_library_cache_over_quota == 1` (or used/quota above a threshold) and on a
    // size that stays unknown or a measurement that is long overdue.
    if (libraryStore) {
        gauge('lumi_library_cache_used_bytes', 'Allocated size of the per-package library cache (the remembered figure while it is being measured).',
            () => libraryStore.usage().usedBytes)
        gauge('lumi_library_cache_quota_bytes', 'Soft quota of the library cache in bytes; 0 = no quota.',
            () => libraryStore.usage().quotaBytes)
        gauge('lumi_library_cache_over_quota', '1 while the library cache is larger than its soft quota.',
            () => libraryStore.usage().overQuota ? 1 : 0)
        gauge('lumi_library_cache_size_known', '0 until the size of the library cache is known (first start, measurement not finished).',
            () => libraryStore.usage().known ? 1 : 0)
        gauge('lumi_library_cache_measuring', '1 while the library cache is being measured.',
            () => libraryStore.usage().reconciling ? 1 : 0)
        gauge('lumi_library_cache_last_measured_timestamp_seconds', 'Unix time of the last complete measurement of the library cache; 0 = none yet.',
            () => (libraryStore.usage().measuredAt ?? 0) / 1000)
    }
}
