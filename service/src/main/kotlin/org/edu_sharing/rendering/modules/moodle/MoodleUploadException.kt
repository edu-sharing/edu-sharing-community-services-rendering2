package org.edu_sharing.rendering.modules.moodle

class MoodleUploadException(
    message: String,
    val publicMessage: String
) : Exception(message)
