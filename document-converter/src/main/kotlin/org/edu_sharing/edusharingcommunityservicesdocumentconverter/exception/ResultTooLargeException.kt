package org.edu_sharing.edusharingcommunityservicesdocumentconverter.exception

/** The converted result cannot be post-processed within the configured limits (mapped to HTTP 413). */
class ResultTooLargeException(message: String) : Exception(message)
