package org.edu_sharing.rendering.modules

interface ThirdPartyModule {
    fun validateThirdPartyCredentials(credentials: Map<String, String>, repoId: String)
    fun getConfig(repoId: String): Map<String, String>
    fun validateCredentials(credentials: Map<String, String>, requiredCredentialKeys: Set<String>, moduleName: String) {
        var missingKeys = mutableListOf<String>()
        var emptyValues = mutableListOf<String>()
        requiredCredentialKeys.forEach {
            if (!credentials.containsKey(it)) {
                missingKeys.add(it)
            } else if (credentials[it].isNullOrEmpty()) {
                emptyValues.add(it)
            }
        }
        if (missingKeys.isNotEmpty() || emptyValues.isNotEmpty()) {
            val builder = StringBuilder("Cannot register $moduleName service.")
            if (missingKeys.isNotEmpty()) {
                builder.append("The following keys are missing: ${missingKeys.joinToString(",")}.")
            }
            if (emptyValues.isNotEmpty()) {
                builder.append("The following values are empty: ${emptyValues.joinToString(",")}.")
            }
            throw IllegalArgumentException(builder.toString())
        }
    }
}