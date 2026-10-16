package org.edu_sharing.rendering.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class SchedulingConfig {

    /**
     * Pool size of the shared scheduler. Several `@Scheduled` jobs run on it (cache cleaner, CORS
     * sync, job reaper, legacy-queue cleaner, ...); with only 2 threads one long-running job (e.g.
     * a cache cleanup) starves the others. At least 4, tunable via `app.scheduling.pool-size`.
     */
    @Bean
    fun taskScheduler(@Value($$"${app.scheduling.pool-size:4}") poolSize: Int): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = poolSize.coerceAtLeast(MIN_POOL_SIZE)
        scheduler.setThreadNamePrefix("scheduled-task-")
        scheduler.initialize()
        return scheduler
    }

    private companion object {
        const val MIN_POOL_SIZE = 4
    }
}
