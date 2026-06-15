package org.edu_sharing.rendering.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the Redis session serializer against the Spring Boot 4 regression where
 * [org.springframework.security.jackson.SecurityJacksonModules] activates default typing with a
 * Spring-Security-only [tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator], which then
 * rejected ordinary session values such as `java.lang.Long` with
 * "Could not resolve type id 'java.lang.Long' ... denied resolution" during
 * `RedisIndexedSessionRepository.onMessage`.
 */
class SessionConfigTest {

    private val serializer = SessionConfig()
        .apply { setBeanClassLoader(javaClass.classLoader) }
        .springSessionDefaultRedisSerializer()

    @Test
    fun `session map with a Long value round-trips`() {
        // Mirrors the Map<String, Object> Spring Session serializes for its session-created event,
        // including the `lastAccessedTime` Long that triggered the original failure.
        val session = hashMapOf<String, Any>(
            "lastAccessedTime" to 123L,
            "maxInactiveInterval" to 1800,
            "creationTime" to 100L,
            "attr" to "value",
        )

        val bytes = serializer.serialize(session)
        val restored = serializer.deserialize(bytes)

        assertEquals(session, restored)
    }
}
