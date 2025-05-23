package org.edu_sharing.rendering.edusharingRepo

import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class EncryptionServiceTest {
    private val underTest = EncryptionService("B374A26A71490437AA024E4FADD5B497")

    @Test
    fun test() {
        val encrypted = underTest.encrypt("test")
        val decrypted = underTest.decrypt(encrypted)
        assert(decrypted == "test")
    }
}