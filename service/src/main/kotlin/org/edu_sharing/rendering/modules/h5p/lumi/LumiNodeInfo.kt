package org.edu_sharing.rendering.modules.h5p.lumi
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash

@RedisHash
data class LumiNodeInfo(
    @Id
    val lumiId: String,
    val nodeId: String,
    val hash: String
)
