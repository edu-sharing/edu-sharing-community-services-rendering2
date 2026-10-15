package org.edu_sharing.rendering.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

class SchedulingConfigTest {

    private fun poolSize(configured: Int): Int {
        val scheduler = SchedulingConfig().taskScheduler(configured) as ThreadPoolTaskScheduler
        try {
            return scheduler.scheduledThreadPoolExecutor.corePoolSize
        } finally {
            scheduler.shutdown()
        }
    }

    @Test
    fun `pool size is configurable`() = assertEquals(8, poolSize(8))

    @Test
    fun `pool size never drops below four`() = assertEquals(4, poolSize(1))
}
