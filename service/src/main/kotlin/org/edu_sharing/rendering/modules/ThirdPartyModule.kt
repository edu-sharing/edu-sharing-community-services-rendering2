package org.edu_sharing.rendering.modules

interface ThirdPartyModule {
    fun validateThirdPartyCredentials(credentials: Map<String, String>)
    fun getConfig(repoId: String): Map<String, String>
}