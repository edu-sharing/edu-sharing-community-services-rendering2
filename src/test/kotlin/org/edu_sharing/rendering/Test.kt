package org.edu_sharing.rendering

import org.apache.commons.codec.binary.Base64
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class Test {
    @Test
    fun testKeyPair() {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()
        val privateKey = String(Base64().encode(keyPair.private.encoded))
        val publicKey =
            "-----BEGIN PUBLIC KEY-----\n" + String(Base64().encode(keyPair.public.encoded)) + "-----END PUBLIC KEY-----"
        val sigData =  "Marian" + System.currentTimeMillis()
        val keySpec = PKCS8EncodedKeySpec(java.util.Base64.getDecoder().decode(privateKey))
        val pkey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        val cipher = Cipher.getInstance("RSA")
        cipher.init(Cipher.ENCRYPT_MODE, pkey)
        val encryptedSigData = cipher.doFinal(sigData.toByteArray())
        val publicKeyData = publicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n","")
        val pkeySpec = X509EncodedKeySpec(java.util.Base64.getDecoder().decode(publicKeyData))
        val pubKey = KeyFactory.getInstance("RSA").generatePublic(pkeySpec)
        val decryptCipher = Cipher.getInstance("RSA")
        decryptCipher.init(Cipher.DECRYPT_MODE, pubKey)
        val decrypted = String(decryptCipher.doFinal(encryptedSigData))
    }

    @Test
    fun testEncoded() {
        val sig = "mlrhLMEvKj%2FwahMdDGhOYQj5ZKn1I3pR%2B9UHKK8kJxzS6dl%2B0E6P09wzS8hIXQZ0klm2DAlCwiELZMFh1j8DBwfzJQadABfksU%2BNUAoBhtaIuoMyntCP9agOWwWCDWRNC7S%2Fd%2Byt44sYSB1pLU83pFIa8pa49%2Fk9PmSBqYwhQo91vXuKKReurexvFPlNHaDF4qoyvQQyIZzLmL6zaGRe59ay1UemOgTH7MFwNXq%2FFw8XhRhS%2FZk6skdU8CNoudL4isGF95LzZklDwAueOzWUuf7uE9b8Osg7j5nemcb4ie5svI9ZOMhWBZNOHuUKXUiWJOW9nsW2svMwkll%2BJHyPlg%3D%3D"
        val urlSig = URLDecoder.decode(sig)
        val decodedApache = Base64().decode(sig)
        val decodedApache2 = Base64().decode(urlSig)
        val publicKeyData = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA0tMwslh1VoXw0zKEyCEi" +
                "5zUFq2XlwqQUHI/8/cxUJ/lc+Q7l07h4OspFokzMd36N/Oi3tKF1yV0VDcQBpxtu" +
                "YnuZeCOcJjkzr4o3IpldR3J4O6XDYmkwPuMtdA5yk9DxQItVDBR6+MJ5nXYlPQL9" +
                "G6BgiAH4/xVILucEfXg/7QEum2ixiN4n6KKhfsoh6JM+PK8Hx9cKcKBULOtlE7dz" +
                "J1l0tLTh0CGaeZUTKpQM/1I5uNT6F9HudW59bGf4ukb5IXHNGmqJX3dweHLlxhsX" +
                "Gt7lv6H6poOQNrqho1hA/L+IBqcddCojW3vF1G7waOoWC3gkqiYiSRqzQMJIFjPi" +
                "5wIDAQAB"
        val decodedKey = Base64().decode(publicKeyData)
        val spec = X509EncodedKeySpec(decodedKey)
        val kf = KeyFactory.getInstance("RSA")

        val pubKey = kf.generatePublic(spec)

        // publikKey = pubKey; realSig = decodedApache; data = sig

        val dsa = Signature.getInstance("SHA1withRSA")
        dsa.initVerify(pubKey)
        dsa.update(sig.toByteArray())
        val result = dsa.verify(decodedApache)
    }

    @Test
    fun fullTest() {

        // Algorithms
        val keyAlgorithm = "RSA"
        val signingAlgorithm = "SHA1withRSA"

        // Generate key pair like repo
        val generator = KeyPairGenerator.getInstance(keyAlgorithm)
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()
        val privateKey = String(Base64().encode(keyPair.private.encoded))
        val publicKey =
            "-----BEGIN PUBLIC KEY-----\n" + String(Base64().encode(keyPair.public.encoded)) + "-----END PUBLIC KEY-----"

        //get private key from base 64 encoded key
        val decodedPrivKey = Base64().decode(privateKey.toByteArray())
        val privspec = PKCS8EncodedKeySpec(decodedPrivKey)
        val privKeyFactory = KeyFactory.getInstance(keyAlgorithm)
        val privKeyObj = privKeyFactory.generatePrivate(privspec)

        // now sign something
        val data = "W21-34A1234"
        val signDsa = Signature.getInstance(signingAlgorithm)
        signDsa.initSign(privKeyObj)
        signDsa.update(data.toByteArray())
        val signedData = signDsa.sign()

        // now get public key
        var pubkeyString = publicKey.replace("-----BEGIN PUBLIC KEY-----\n", "")
        pubkeyString = pubkeyString.replace("-----END PUBLIC KEY-----", "")
        val decodedPubKey = Base64().decode(pubkeyString.toByteArray())
        val pubKeySpec = X509EncodedKeySpec(decodedPubKey)
        val pubkeyFactory = KeyFactory.getInstance(keyAlgorithm)
        val publicKeyObj = pubkeyFactory.generatePublic(pubKeySpec)

        // now verify
        val verifyDsa = Signature.getInstance(signingAlgorithm)
        verifyDsa.initVerify(publicKeyObj)
        verifyDsa.update(data.toByteArray())
        val result = verifyDsa.verify(signedData)
    }
}