package org.edu_sharing.rendering.security

import org.springframework.stereotype.Service

@Service
class CorsService(private val corsConfig : CorsConfig) {
    fun addOrigin(vararg origins: String){
        origins.forEach(corsConfig::addAllowedOrigin)
    }

    fun addOrigin(origins: List<String>){
        origins.forEach(corsConfig::addAllowedOrigin)
    }

    fun removeOrigin(vararg origins: String) {
        origins.forEach(corsConfig::removeAllowedOrigin)
    }

    fun removeOrigin(origins: List<String>) {
        origins.forEach(corsConfig::removeAllowedOrigin)
    }

    fun getAllowedOrigins(): List<String> =
        corsConfig.getAllowedOrigins()

    fun clearExternalOrigins(){
        corsConfig.clearExternalOrigins()
    }
}
