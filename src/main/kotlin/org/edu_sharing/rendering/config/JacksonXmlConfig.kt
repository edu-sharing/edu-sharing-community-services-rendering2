package org.edu_sharing.rendering.config

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonXmlConfig {


    @Bean
    fun xmlMapper(): XmlMapper {
        val xmlMapper = XmlMapper()
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT)
        return xmlMapper
    }
}