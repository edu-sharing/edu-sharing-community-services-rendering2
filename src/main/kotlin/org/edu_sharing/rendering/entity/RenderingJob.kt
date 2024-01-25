package org.edu_sharing.rendering.entity

import jakarta.persistence.*

@Entity(name = "rendering_job")
@Table(indexes = [
    Index(name = "idx_hash", columnList = "hash"),
])
data class RenderingJob (
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long = 0,
    @Column(name = "hash")
    val hash: String,
    @Column(name = "type")
    val type: String,
    @Column(name = "status")
    val status: String
)
