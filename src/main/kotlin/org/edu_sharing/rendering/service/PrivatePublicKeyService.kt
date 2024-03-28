package org.edu_sharing.rendering.service

import java.security.PrivateKey
import java.security.PublicKey

interface PrivatePublicKeyService {
    fun getRepositoryKey(): PublicKey
    fun getPrivateKey(): PrivateKey
    fun generateApplicationKeyPair()
    fun storeRepositoryKey(publicKey: String)
    fun hasKeyPaare() : Boolean
    fun hasRepositoryKey(): Boolean
}
