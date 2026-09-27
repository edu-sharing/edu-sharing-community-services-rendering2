package org.edu_sharing.rendering.integration

import org.edu_sharing.rendering.modules.ModuleRegistry
import org.edu_sharing.rendering.modules.RenderModule
import org.edu_sharing.rendering.modules.document.DocumentRenderModule
import org.edu_sharing.rendering.modules.document.SpreadsheetRenderModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertIs

/**
 * Narrowing `app.converter.document.extensions` must not take a whole module down with it. Only
 * `odt` is configured here, so nothing routes to the spreadsheet module - it still has to be
 * resolvable by name, because RegistrationRunner activates the optional SPREADSHEET module that way
 * during startup and an unresolvable name aborted the boot.
 */
@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "app.roles=master",
        "app.storage.minio.bucket.mode=byType",
        "app.converter.spreadsheetToHtml.enabled=true",
        "app.security.enabled=false",
        "app.converter.document.extensions=odt"
    ]
)
class DocumentExtensionsRegistrationTest(
    @param:Autowired val moduleRegistry: ModuleRegistry
) : AbstractIntegrationTest() {

    @Test
    fun bothDocumentModulesStayResolvableByNameWithASingleConfiguredExtension() {
        assertDoesNotThrow {
            assertIs<SpreadsheetRenderModule>(moduleRegistry.getRenderModule<RenderModule>("SPREADSHEET"))
            assertIs<DocumentRenderModule>(moduleRegistry.getRenderModule<RenderModule>("DOCUMENT"))
        }
    }
}
