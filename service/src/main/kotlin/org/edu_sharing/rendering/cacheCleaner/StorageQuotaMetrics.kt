package org.edu_sharing.rendering.cacheCleaner

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.edu_sharing.rendering.core.annotation.ConditionalOnMaster
import org.edu_sharing.rendering.storage.StorageInfo
import org.edu_sharing.rendering.storage.StorageScopeKind
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Exposes how full every quota scope is, so that exceeding a quota can raise an alarm.
 *
 * Quotas here are soft: nothing refuses a request because a bucket or lumi's library cache is over its quota.
 * The [CacheCleaner] frees them on a schedule, and if it cannot keep up - it is not running, it fails, a quota is
 * simply too small for what is being rendered - the only symptom is a volume that eventually runs full. These
 * gauges are what makes that visible in time. One set per scope (a bucket, a whole repo, or lumi's H5P library
 * cache), tagged `repo`, `bucket` (`*` for a repo-wide scope) and `kind` (`bucket`, `library_cache`):
 *
 * - `rendering.storage.used.bytes` / `rendering.storage.quota.bytes` (0 = no quota)
 * - `rendering.storage.usage.ratio`: used / quota, 0 without a quota
 * - `rendering.storage.over.quota`: 1 while used > quota
 * - `rendering.storage.over.threshold`: 1 while the ratio is above the cleaner's upper threshold, which is when
 *   it starts deleting. Staying at 1 across runs means the cleaner does not manage to free enough.
 *
 * and for the cleaner itself `rendering.cache.cleaner.deleted.entries` / `.freed.bytes` (counters, tag `kind`),
 * `.failures` and `.last.run.timestamp.seconds` (alert when it stops advancing).
 *
 * The values are those of the last cleaner run, which is also the only time the sizes are collected. After a
 * cleanup they are the sizes estimated from what was freed, not the ones before it.
 */
/** Only on the master, like the [CacheCleaner] that feeds it: elsewhere the gauges would report a cleaner that never ran. */
@Component
@ConditionalOnMaster
class StorageQuotaMetrics(private val meterRegistry: MeterRegistry) {

    private class Scope(val gauges: List<Meter>) {
        val used = AtomicLong()
        val quota = AtomicLong()
        val upperThreshold = AtomicLong(java.lang.Double.doubleToLongBits(1.0))
    }

    private val scopes = ConcurrentHashMap<String, Scope>()
    private val lastRunSeconds = AtomicLong()

    init {
        Gauge.builder("rendering.cache.cleaner.last.run.timestamp.seconds", lastRunSeconds) { it.get().toDouble() }
            .description("Unix time of the last finished cache cleaner run; 0 = none since the start.")
            .register(meterRegistry)
    }

    /** Records the size of [scope] as [usedBytes] (the observed size, or the one estimated after a cleanup). */
    fun record(scope: StorageInfo, usedBytes: Long, upperThreshold: Float) {
        val entry = scopes.computeIfAbsent(keyOf(scope)) { register(scope) }
        entry.used.set(usedBytes)
        entry.quota.set(scope.maxSize)
        entry.upperThreshold.set(java.lang.Double.doubleToLongBits(upperThreshold.toDouble()))
    }

    /** Stops reporting scopes that are no longer there (a deleted repo, a bucket without tracked objects). */
    fun retainOnly(current: Collection<StorageInfo>) {
        val keep = current.map { keyOf(it) }.toSet()
        scopes.keys.filter { it !in keep }.forEach { key ->
            scopes.remove(key)?.gauges?.forEach { meterRegistry.remove(it) }
        }
    }

    fun cleaned(kind: StorageScopeKind, entries: Int, bytes: Long) {
        val tags = Tags.of("kind", kind.metricTag())
        meterRegistry.counter("rendering.cache.cleaner.deleted.entries", tags).increment(entries.toDouble())
        meterRegistry.counter("rendering.cache.cleaner.freed.bytes", tags).increment(bytes.toDouble())
    }

    fun failure(kind: StorageScopeKind) {
        meterRegistry.counter("rendering.cache.cleaner.failures", Tags.of("kind", kind.metricTag())).increment()
    }

    fun runFinished() {
        lastRunSeconds.set(System.currentTimeMillis() / 1000)
    }

    private fun register(scope: StorageInfo): Scope {
        val tags = Tags.of("repo", scope.repoId, "bucket", scope.bucket ?: "*", "kind", scope.kind.metricTag())
        lateinit var holder: Scope
        fun gauge(name: String, description: String, baseUnit: String?, value: (Scope) -> Double): Meter =
            Gauge.builder(name, this) { value(holder) }
                .description(description)
                .tags(tags)
                .also { if (baseUnit != null) it.baseUnit(baseUnit) }
                .register(meterRegistry)

        val gauges = listOf(
            gauge("rendering.storage.used.bytes", "Size of the quota scope.", "bytes") { it.used.get().toDouble() },
            gauge("rendering.storage.quota.bytes", "Quota of the scope; 0 = none.", "bytes") { it.quota.get().toDouble() },
            gauge("rendering.storage.usage.ratio", "Size / quota of the scope; 0 without a quota.", null) { it.ratio() },
            gauge("rendering.storage.over.quota", "1 while the scope is larger than its quota.", null) {
                if (it.quota.get() > 0 && it.used.get() > it.quota.get()) 1.0 else 0.0
            },
            gauge(
                "rendering.storage.over.threshold",
                "1 while the scope is above the upper threshold of the cache cleaner, i.e. when it starts deleting.",
                null
            ) { if (it.quota.get() > 0 && it.ratio() > java.lang.Double.longBitsToDouble(it.upperThreshold.get())) 1.0 else 0.0 }
        )
        holder = Scope(gauges)
        return holder
    }

    private fun Scope.ratio(): Double = if (quota.get() > 0) used.get().toDouble() / quota.get() else 0.0

    private fun keyOf(scope: StorageInfo) = "${scope.repoId}|${scope.bucket ?: "*"}|${scope.kind}"

    private fun StorageScopeKind.metricTag() = name.lowercase()
}
