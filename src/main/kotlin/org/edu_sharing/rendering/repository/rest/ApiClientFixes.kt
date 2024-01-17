package org.edu_sharing.rendering.repository.rest

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.GsonBuilder
import okhttp3.*
import okhttp3.Headers.Companion.headersOf
import org.edu_sharing.generated.repository.backend.services.rest.client.*
import org.edu_sharing.generated.repository.backend.services.rest.client.auth.ApiKeyAuth
import org.edu_sharing.generated.repository.backend.services.rest.client.auth.Authentication
import org.edu_sharing.generated.repository.backend.services.rest.client.auth.HttpBasicAuth
import java.net.URI
import java.time.OffsetDateTime

class ApiClientFixes() : ApiClient() {
    private val authentications: MutableMap<String, Authentication> = HashMap()

    init {
        init()
    }

    private fun init() {
        authentications.putAll(super.getAuthentications())
        authentications.putIfAbsent("basicAuth", HttpBasicAuth())
        JSON.setGson(GsonBuilder().addDeserializationExclusionStrategy(object: ExclusionStrategy {
            override fun shouldSkipField(p0: FieldAttributes?): Boolean {
                return false
            }

            override fun shouldSkipClass(p0: Class<*>?): Boolean {
                return p0 == OffsetDateTime::class.java
            }
        }).create())
    }

    override fun getAuthentications(): Map<String, Authentication> {
        return authentications
    }

    override fun getAuthentication(authName: String): Authentication {
        return authentications[authName]!!
    }

    override fun setUsername(username: String) {
        val iterator: Iterator<Authentication> = authentications.values.iterator()
        var auth: Authentication
        do {
            if (!iterator.hasNext()) {
                throw RuntimeException("No HTTP basic authentication configured!")
            }
            auth = iterator.next()
        } while (auth !is HttpBasicAuth)
        auth.username = username
    }

    override fun setPassword(password: String) {
        val iterator: Iterator<Authentication> = this.authentications.values.iterator()
        var auth: Authentication
        do {
            if (!iterator.hasNext()) {
                throw RuntimeException("No HTTP basic authentication configured!")
            }
            auth = iterator.next()
        } while (auth !is HttpBasicAuth)
        auth.password = password
    }

    override fun setApiKey(apiKey: String) {
        val iterator: Iterator<Authentication> = this.authentications.values.iterator()
        var auth: Authentication
        do {
            if (!iterator.hasNext()) {
                throw RuntimeException("No API key authentication configured!")
            }
            auth = iterator.next()
        } while (auth !is ApiKeyAuth)
        auth.apiKey = apiKey
    }

    override fun setApiKeyPrefix(apiKeyPrefix: String) {
        val iterator: Iterator<Authentication> = this.authentications.values.iterator()
        var auth: Authentication
        do {
            if (!iterator.hasNext()) {
                throw RuntimeException("No API key authentication configured!")
            }
            auth = iterator.next()
        } while (auth !is ApiKeyAuth)
        auth.apiKeyPrefix = apiKeyPrefix
    }

    @Throws(ApiException::class)
    override fun updateParamsForAuth(
            authNames: Array<String>,
            queryParams: List<Pair>,
            headerParams: Map<String, String>,
            cookieParams: Map<String, String>,
            payload: String,
            method: String,
            uri: URI
    ) {
        for (authName in authNames) {
            val auth = this.authentications[authName]
                    ?: throw RuntimeException("Authentication undefined: $authName")
            auth.applyToParams(queryParams, headerParams, cookieParams, payload, method, uri)
        }
    }

    /**
     * fix to support upload of ByteArray
     */
    override fun buildRequestBodyMultipart(formParams: Map<String?, Any?>): RequestBody {
        val mpBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val var3: Iterator<*> = formParams.entries.iterator()
        while (var3.hasNext()) {
            val (key, value) = var3.next() as Map.Entry<*, *>
            if (value is ByteArray) {
                val partHeaders: Headers = headersOf(
                        "Content-Disposition",
                        "form-data; name=\"" + key as String + "\"; filename=\"" + key+ "\""
                )
                mpBuilder.addPart(partHeaders, RequestBody.create(null, value))
            } else {
                val partHeaders: Headers =
                        headersOf("Content-Disposition", "form-data; name=\"" + key as String + "\"")
                mpBuilder.addPart(partHeaders, RequestBody.create(null, parameterToString(value)))
            }
        }
        return mpBuilder.build()
    }

    @Throws(ApiException::class)
    override fun buildCall(
            baseUrl: String?,
            path: String,
            method: String,
            queryParams: List<Pair>,
            collectionQueryParams: List<Pair>,
            body: Any?,
            headerParams: MutableMap<String, String>,
            cookieParams: Map<String, String>,
            formParams: Map<String, Any>,
            authNames: Array<String>,
            callback: ApiCallback<*>?
    ): okhttp3.Call? {
        var authNames: Array<String>? = authNames
        headerParams.putIfAbsent("Content-Type", "application/json")
        if (authNames == null || authNames.size == 0) {
            authNames = arrayOf("basicAuth")
        }
        return super.buildCall(
                baseUrl ?: this.basePath,
                path,
                method,
                queryParams,
                collectionQueryParams,
                body,
                headerParams,
                cookieParams,
                formParams,
                authNames,
                callback
        )
    }
}