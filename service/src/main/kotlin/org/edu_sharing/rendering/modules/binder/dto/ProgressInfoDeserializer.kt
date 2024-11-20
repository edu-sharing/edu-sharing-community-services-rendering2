package org.edu_sharing.rendering.modules.binder.dto

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

class ProgressInfoDeserializer: JsonDeserializer<ProgressInfo>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext
    ): ProgressInfo {
        return when {
            parser.isExpectedStartObjectToken -> {
                val objectValue = parser.readValueAs(ProgressInfoObject::class.java)
                ProgressInfo(progressObject = objectValue)
            }
            parser.currentToken.isScalarValue -> {
                val stringValue = parser.text
                ProgressInfo(progressString = stringValue)
            }
            else -> throw IllegalStateException("Unexpected JSON token: ${parser.currentToken}")
        }
    }
}