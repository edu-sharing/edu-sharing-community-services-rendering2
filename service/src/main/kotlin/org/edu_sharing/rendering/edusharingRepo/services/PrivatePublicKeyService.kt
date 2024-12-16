package org.edu_sharing.rendering.edusharingRepo.services

import java.security.PrivateKey
import java.security.PublicKey

interface PrivatePublicKeyService {
    fun getPrivateKey(): PrivateKey
    fun generateApplicationKeyPair()
    fun hasKeyPair() : Boolean
}
