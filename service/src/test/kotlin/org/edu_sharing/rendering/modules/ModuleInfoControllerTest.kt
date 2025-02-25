package org.edu_sharing.rendering.modules

import com.ninjasquad.springmockk.MockkBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc

@WebMvcTest(ModuleInfoController::class, excludeAutoConfiguration = [SecurityAutoConfiguration::class])
class ModuleInfoControllerTest(@Autowired val mockMvc: MockMvc) {

    @MockkBean
    lateinit var moduleRegistry: ModuleRegistry

    private val registryTestClass = ModuleRegistryTest()

    /*@Test
    fun testGetModulesInfoReturnsAllRegisteredTypes() {
        // Arrange
        registryTestClass.setUp()
        every { moduleRegistry.getModuleTypeMapperList() } returns registryTestClass.underTest.getModuleTypeMapperList()

        // Act
        val result = mockMvc.perform(get("/info/modules"))
            .andExpect(status().isOk)
            .andReturn()

        // Assert
        val json = result.response.contentAsString
        val typeToken = object : TypeToken<List<RenderModuleInfo>>() {}.type
        val returnList = Gson().fromJson<List<RenderModuleInfo>>(json, typeToken)

        assert(returnList.size == 3)

        val typeModuleEntry = returnList.firstOrNull {it.name == "typeModule"}
        assert(typeModuleEntry != null)
        assert(typeModuleEntry?.typeMapping?.type == "file-any")
        assert(typeModuleEntry?.typeMapping?.mimeTypeSuffix == null)
        assert(typeModuleEntry?.typeMapping?.mimeTypePrefix == null)

        val mimeTypeModuleEntry = returnList.firstOrNull {it.name == "suffixModule"}
        assert(mimeTypeModuleEntry != null)
        assert(mimeTypeModuleEntry?.typeMapping?.type == null)
        assert(mimeTypeModuleEntry?.typeMapping?.mimeTypePrefix == "prefix-any")
        assert(mimeTypeModuleEntry?.typeMapping?.mimeTypeSuffix == "suffix-with-prefix")

        val prefixTypeModuleEntry = returnList.firstOrNull {it.name == "prefixModule"}
        assert(prefixTypeModuleEntry != null)
        assert(prefixTypeModuleEntry?.typeMapping?.type == null)
        assert(prefixTypeModuleEntry?.typeMapping?.mimeTypePrefix == "prefix-any")
        assert(prefixTypeModuleEntry?.typeMapping?.mimeTypeSuffix == null)
    }*/
}