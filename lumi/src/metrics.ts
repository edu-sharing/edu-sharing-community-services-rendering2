import * as promClient from 'prom-client'
import {getImportQueueStatus} from './importQueue'
import {getS3PoolStatus} from './s3Pool'

/**
 * Prometheus gauges for the two things that can silently stop every package import: a stuck import
 * and an exhausted S3 pool. Registered in the default registry, which express-prom-bundle serves.
 * Values are read when Prometheus scrapes, so nothing has to be kept in sync.
 */
export const registerImportMetrics = (): void => {
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
}
