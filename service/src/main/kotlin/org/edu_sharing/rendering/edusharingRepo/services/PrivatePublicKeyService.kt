package org.edu_sharing.rendering.edusharingRepo.services

import java.security.PrivateKey
import java.security.PublicKey

interface PrivatePublicKeyService {
    fun getRepositoryKey(): PublicKey
    fun getPrivateKey(): PrivateKey
    fun generateApplicationKeyPair()
    fun storeRepositoryKey(publicKey: String)
    fun hasKeyPair() : Boolean
    fun hasRepositoryKey(): Boolean
}
