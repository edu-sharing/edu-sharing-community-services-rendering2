package org.edu_sharing.rendering.edusharingRepo

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class EncryptionService(
    @Value("\${app.security.encryptionkey}")
    private val key: String
) {
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BIT = 128
        private const val IV_LENGTH_BYTE = 12
    }

    private val keySpec: SecretKeySpec = SecretKeySpec(hexStringToByteArray(key), "AES")


    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)

        // Generate a random IV
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)

        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val ivAndCiphertext = iv + ciphertext

        return Base64.getEncoder().encodeToString(ivAndCiphertext)
    }

    fun decrypt(encryptedValue: String): String {
        val ivAndCiphertext = Base64.getDecoder().decode(encryptedValue)

        val iv = ivAndCiphertext.copyOfRange(0, IV_LENGTH_BYTE)
        val ciphertext = ivAndCiphertext.copyOfRange(IV_LENGTH_BYTE, ivAndCiphertext.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) {
            hex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
}