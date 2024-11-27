package org.edu_sharing.rendering.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app")
class AppInfo {
    lateinit var appId: String
    lateinit var appCaption: String
    lateinit var public: Public

    data class Public(var protocol: String, var host: String, var port: Short, var url: String)
}

