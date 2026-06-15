package org.edu_sharing.rendering.modules.binder.git

import tools.jackson.databind.ObjectMapper
import org.edu_sharing.rendering.modules.binder.dto.GitDetails
import org.slf4j.LoggerFactory
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
    private val gitHubBinaryWebClient: WebClient
): GitService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Throws (Exception::class)
    override fun getFile(
        gitDetails: GitDetails,
        token: String
    ): ByteArrayInputStream {
        if (gitDetails.filePath.isNullOrBlank()) {
            throw IllegalArgumentException("Could not parse file path from GitHub URL")
        }
        val stream = gitHubBinaryWebClient.get()
            .uri("/${gitDetails.user}/${gitDetails.repo}/${gitDetails.branch}/${gitDetails.filePath}")
            .header("Authorization", "Bearer $token")
            .retrieve()
            .bodyToMono(ByteArray::class.java)
            .map { it.inputStream() }
            .block()
        return stream ?: throw Exception("No stream returned from server")
    }

    override fun checkIfObjectLinkIsUpToDate(
        lastModifiedInCache: Long,
        gitDetails: GitDetails,
        token: String
    ): Boolean {
        try {
            val response = gitHubRepoApiWebClient
                .get()
                .uri {
                    it.path("/${gitDetails.user}/${gitDetails.repo}/commits")
                        .queryParam("path", gitDetails.filePath ?: "")
                        .queryParam("sha", gitDetails.branch)
                        .build()
                }
                .header("Authorization", "Bearer $token")
                .retrieve().bodyToMono(String::class.java).block()
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
        } catch (exception: Exception) {
            // ToDo send info to client to inform of potentially deprecated preview from cache
            log.warn("Error calling github api for file check. Existing cached preview will be used.", exception)
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