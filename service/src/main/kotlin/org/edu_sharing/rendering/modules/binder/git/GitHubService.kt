package org.edu_sharing.rendering.modules.binder.git

import tools.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.modules.binder.dto.GitDetails
import org.edu_sharing.rendering.utils.LogSanitizer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.util.unit.DataSize
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.io.ByteArrayInputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Matcher
import java.util.regex.Pattern

@Service
class GitHubService(
    private val gitHubRepoApiWebClient: WebClient,
    private val gitHubBinaryWebClient: WebClient,
    @param:Value($$"${app.module.binder.max-file-size:50MB}")
    private val maxFileSize: DataSize
): GitService {

    private companion object {
        val FILE_TIMEOUT: Duration = Duration.ofSeconds(60)
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @Throws (Exception::class)
    override fun getFile(
        gitDetails: GitDetails,
        token: String
    ): ByteArrayInputStream {
        if (gitDetails.filePath.isNullOrBlank()) {
            throw IllegalArgumentException("Could not parse file path from GitHub URL")
        }
        log.debug("Fetching file from GitHub: user={}, repo={}, branch={}, filePath={}", gitDetails.user, gitDetails.repo, gitDetails.branch, gitDetails.filePath)
        // Streamed and joined against an explicit limit instead of bodyToMono(ByteArray), which is capped by the
        // shared codec buffer limit (spring.http.codecs.max-in-memory-size) and fails on larger notebooks.
        val maxBytes = maxFileSize.toBytes().toInt()
        val stream = try {
            DataBufferUtils.join(
                gitHubBinaryWebClient.get()
                    .uri("/${gitDetails.user}/${gitDetails.repo}/${gitDetails.branch}/${gitDetails.filePath}")
                    .header("Authorization", "Bearer $token")
                    .retrieve()
                    .bodyToFlux(DataBuffer::class.java),
                maxBytes
            )
                .map { buffer ->
                    try {
                        ByteArray(buffer.readableByteCount()).also { buffer.read(it) }
                    } finally {
                        DataBufferUtils.release(buffer)
                    }
                }
                .map { it.inputStream() }
                .timeout(FILE_TIMEOUT)
                .block()
        } catch (exception: WebClientResponseException) {
            throw IllegalStateException(describeFailure(exception, "fetching ${gitDetails.filePath}"), exception)
        } catch (exception: DataBufferLimitException) {
            throw IllegalStateException(
                "File ${gitDetails.filePath} in ${gitDetails.user}/${gitDetails.repo} exceeds the limit of $maxFileSize",
                exception
            )
        }
        return stream ?: throw Exception("No stream returned from server")
    }

    /** Readable, credential-free reason for a failed GitHub call; spells out rate limiting (403/429). */
    internal fun describeFailure(exception: WebClientResponseException, action: String): String {
        val status = exception.statusCode.value()
        return when {
            status == 429 || (status == 403 && exception.headers.getFirst("X-RateLimit-Remaining") == "0") ->
                "GitHub rate limit reached while $action (retry-after=${exception.headers.getFirst("Retry-After")}, " +
                    "reset=${exception.headers.getFirst("X-RateLimit-Reset")})"

            status == 403 -> "GitHub denied access (403) while $action; check the configured token"
            status == 404 -> "GitHub resource not found (404) while $action"
            else -> "GitHub answered $status while $action"
        }
    }

    override fun checkIfObjectLinkIsUpToDate(
        lastModifiedInCache: Long,
        gitDetails: GitDetails,
        token: String
    ): Boolean {
        log.debug("Checking GitHub commit freshness: user={}, repo={}, branch={}, filePath={}, lastModifiedInCache={}", gitDetails.user, gitDetails.repo, gitDetails.branch, gitDetails.filePath, lastModifiedInCache)
        try {
            val response = gitHubRepoApiWebClient
                .get()
                .uri {
                    it.path("/${gitDetails.user}/${gitDetails.repo}/commits")
                        .queryParam("path", gitDetails.filePath ?: "")
                        .queryParam("sha", gitDetails.branch)
                        // Only the latest commit is inspected; do not transfer the default page of 30.
                        .queryParam("per_page", 1)
                        .build()
                }
                .header("Authorization", "Bearer $token")
                .retrieve().bodyToMono(String::class.java)
                .timeout(FILE_TIMEOUT)
                .block()
            response?.let {
                val objectMapper = ObjectMapper()
                val rootNode = objectMapper.readTree(it)
                if (rootNode.isArray && rootNode.size() > 0) {
                    val dateStr = rootNode[0]
                        .path("commit")
                        .path("committer")
                        .path("date")
                        .asString()
                    val commitTimeStamp =
                        ZonedDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME).toInstant().epochSecond
                    return commitTimeStamp < lastModifiedInCache
                }
            }
        } catch (exception: WebClientResponseException) {
            // 403/429 (rate limit, token) and other HTTP errors are expected operationally: no stack trace.
            log.warn(
                "GitHub freshness check failed, existing cached preview will be used: {}",
                describeFailure(exception, "checking commits of ${gitDetails.user}/${gitDetails.repo}")
            )
            return true
        } catch (exception: Exception) {
            // ToDo send info to client to inform of potentially deprecated preview from cache
            log.warn("Error calling github api for file check. Existing cached preview will be used.", LogSanitizer.sanitize(exception))
            return true
        }
        return false
    }

    override fun identifyUrl(url: String) = url.contains("github.com")

    override fun identifyDeepLink(url: String) = url.endsWith(".ipynb")

    @Throws (IllegalArgumentException::class)
    override fun getGitDetailsFromUrl(url: String): GitDetails {
        val isDeepLink = identifyDeepLink(url)
        val regex = if (isDeepLink) "https://github\\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.*)"
            else "https://github\\.com/([^/]+)/([^/]+)(?:/tree/([^/]+))?"
        val pattern: Pattern = Pattern.compile(regex)
        val matcher: Matcher = pattern.matcher(url)

        if (!matcher.find()) {
            throw IllegalArgumentException("GitHub URL for Binder import must contain user and repo")
        }

        val user = matcher.group(1) ?: throw IllegalArgumentException("GitHub URL for Binder must contain user")
        val repository =
            matcher.group(2) ?: throw IllegalArgumentException("GitHub URL for Binder must contain repository")
        val branch = if (matcher.group(3) != null) matcher.group(3) else "main"
        var filePath: String? = null
        if (isDeepLink) {
            filePath = matcher.group(4)
        }

        return GitDetails(
            user = user,
            repo = repository,
            branch = branch,
            filePath = filePath
        )
    }
}