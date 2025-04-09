package org.edu_sharing.rendering.modules.binder.git

import org.edu_sharing.rendering.modules.binder.dto.GitDetails
import java.io.ByteArrayInputStream

interface GitService {
    @Throws (Exception::class)
    fun getFile(gitDetails: GitDetails, token: String): ByteArrayInputStream
    fun checkIfObjectLinkIsUpToDate(lastModifiedInCache: Long, gitDetails: GitDetails, token: String): Boolean
    fun identifyUrl(url: String): Boolean
    fun identifyDeepLink(url: String): Boolean
    @Throws (IllegalArgumentException::class)
    fun getGitDetailsFromUrl(url: String): GitDetails
}