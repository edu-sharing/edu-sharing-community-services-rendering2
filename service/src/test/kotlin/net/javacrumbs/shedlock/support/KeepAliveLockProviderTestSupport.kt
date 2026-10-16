package net.javacrumbs.shedlock.support

import net.javacrumbs.shedlock.core.ExtensibleLockProvider
import net.javacrumbs.shedlock.core.LockProvider
import java.time.Duration
import java.util.concurrent.ScheduledExecutorService

/**
 * Lives in the library's package only to reach its package-private constructor with a short minimal
 * `lockAtMostFor` (the public one demands 30 s), so a test can watch an extension within a second.
 */
fun keepAliveProviderWithShortMinimum(
    wrapped: ExtensibleLockProvider,
    executor: ScheduledExecutorService,
    minimalLockAtMostFor: Duration,
): LockProvider = KeepAliveLockProvider(wrapped, executor, minimalLockAtMostFor)
