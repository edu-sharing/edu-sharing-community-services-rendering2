package org.edu_sharing.edusharingcommunityservicesdocumentconverter.dto

/**
 * Represents an error message that can be returned as a response in case of an exception.
 *
 * @property status The HTTP status code associated with the error.
 * @property message A descriptive message explaining the error.
 */
data class ErrorMessage(
    var status: Int? = null,
    var message: String? = null
)
