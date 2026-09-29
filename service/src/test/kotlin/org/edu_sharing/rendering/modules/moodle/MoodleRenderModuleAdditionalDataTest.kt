package org.edu_sharing.rendering.modules.moodle

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.edu_sharing.rendering.edusharingRepo.entity.ModuleSettings
import org.edu_sharing.rendering.edusharingRepo.entity.RepositoryRegistration
import org.edu_sharing.rendering.edusharingRepo.services.RepositoryRegistrationStorageService
import org.edu_sharing.rendering.renderingJob.entity.SubJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Optional

/**
 * The Moodle/SCORM display switch travels to the rendering frontend through the job-info
 * `additionalData` map. Two things have to hold: it defaults to on (so an installation that never
 * configured it keeps the embedded course it always had), and nothing else from the credentials -
 * above all the Moodle webservice token - is ever handed to the browser.
 */
@ExtendWith(MockKExtension::class)
class MoodleRenderModuleAdditionalDataTest {

    private val repoId = "repo1"
    private val linkUrl = "https://moodle.example.org/local/edusharing_webservice/forwardUser.php?token=usertoken"

    private val repositoryRegistrationStorageService = mockk<RepositoryRegistrationStorageService>()
    private val moodleJobService = mockk<MoodleJobService>(relaxed = true)

    private lateinit var registration: RepositoryRegistration
    private lateinit var subJob: SubJob
    private lateinit var underTest: MoodleRenderModule

    @BeforeEach
    fun setup() {
        registration = RepositoryRegistration(
            repoId = repoId,
            url = "http://repo.url",
            publicKey = "key"
        )
        every { repositoryRegistrationStorageService.getRegistrationByRepoId(repoId) } returns Optional.of(registration)

        subJob = mockk<SubJob>()
        every { subJob.additionalData } returns mapOf("linkUrl" to linkUrl)

        underTest = MoodleRenderModule(55L, moodleJobService, repositoryRegistrationStorageService)
    }

    private fun moodleCredentials(vararg pairs: Pair<String, String>) {
        registration.module["MOODLE"] = ModuleSettings(credentials = mapOf(*pairs))
    }

    @Test
    fun `unconfigured module reports the switch as enabled`() {
        val result = underTest.getAdditionalData(subJob, repoId)

        assertEquals(mapOf("linkUrl" to linkUrl, "showPreviewIframe" to "true"), result)
    }

    /**
     * Only a literal `false` switches a flag off. Anything else - including a typo or a `0` someone
     * expected to work - falls back to the default, so a bad config value can never blank out the
     * renderer. This is deliberately not `String.toBoolean()`, which would treat `1`/`yes` as off.
     */
    @ParameterizedTest
    @CsvSource(
        "false, false",
        "FALSE, false",
        "' false ', false",
        "true, true",
        "TRUE, true",
        "1, true",
        "0, true",
        "yes, true",
        "nope, true",
    )
    fun `only a literal false switches the flag off`(configured: String, expected: String) {
        moodleCredentials("showPreviewIframe" to configured)

        assertEquals(expected, underTest.getAdditionalData(subJob, repoId)["showPreviewIframe"])
    }

    @Test
    fun `no credential other than the display switches reaches the browser`() {
        val token = "s3cr3t-token"
        moodleCredentials(
            "baseurl" to "http://moodle.internal",
            "publicurl" to "https://moodle.example.org",
            "user" to "webservice",
            "password" to "s3cr3t-password",
            "token" to token,
            "categoryid" to "1",
            "timeout" to "90",
            "submitUserDetails" to "true",
            "showPreviewIframe" to "false",
        )

        val result = underTest.getAdditionalData(subJob, repoId)

        assertEquals(setOf("linkUrl", "showPreviewIframe"), result.keys)
        // Checked by value as well: a future rename of a credential key would slip past the key
        // assertion above, but not past this one.
        assertFalse(result.values.any { it.contains(token) })
    }

    @Test
    fun `sub-job data without additional data still carries the switch`() {
        every { subJob.additionalData } returns null

        val result = underTest.getAdditionalData(subJob, repoId)

        assertEquals(mapOf("showPreviewIframe" to "true"), result)
    }

    @Test
    fun `SCORM reads the switch from its own credentials bucket`() {
        moodleCredentials("showPreviewIframe" to "true")
        registration.module["SCORM"] = ModuleSettings(credentials = mapOf("showPreviewIframe" to "false"))

        val scorm = ScormRenderModule(99L, moodleJobService, repositoryRegistrationStorageService)

        assertEquals("true", underTest.getAdditionalData(subJob, repoId)["showPreviewIframe"])
        assertEquals("false", scorm.getAdditionalData(subJob, repoId)["showPreviewIframe"])
    }
}
