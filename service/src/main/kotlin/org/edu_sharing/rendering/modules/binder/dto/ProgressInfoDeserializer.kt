package org.edu_sharing.rendering.modules.binder.dto

import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer

class ProgressInfoDeserializer: ValueDeserializer<ProgressInfo>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext
    ): ProgressInfo {
        return when {
            parser.isExpectedStartObjectToken -> {
                val objectValue = parser.readValueAs(ProgressInfoObject::class.java)
                ProgressInfo(progressObject = objectValue)
            }
            parser.currentToken().isScalarValue -> {
                val stringValue = parser.text
                ProgressInfo(progressString = stringValue)
            }
            else -> throw IllegalStateException("Unexpected JSON token: ${parser.currentToken()}")
        }
    }
}