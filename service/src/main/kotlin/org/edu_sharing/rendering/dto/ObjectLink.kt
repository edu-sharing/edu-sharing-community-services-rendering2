package org.edu_sharing.rendering.dto

data class ObjectLink (
    var width: Int = 0,
    var height: Int = 0,
    val link: String,
    var isHighestQuality: Boolean = false,
)