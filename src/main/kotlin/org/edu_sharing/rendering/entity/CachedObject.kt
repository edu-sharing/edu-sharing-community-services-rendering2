package org.edu_sharing.rendering.entity

import jakarta.persistence.*

@Entity(name = "cached_objects")
@Table(indexes = [Index(name = "idx_hash", columnList = "hash")])
data class CachedObject(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long = 0,
    @Column(name = "object_id")
    val objectId: String,
    @Column(name = "version")
    val version: String,
    @Column(name = "mime_type")
    val mimeType: String,
    @Column(name = "hash")
    val hash: String,
    @Column(name = "last_accessed")
    var lastAccessed: Long,
    @Column(name = "quality")
    val quality: Int? = null
)