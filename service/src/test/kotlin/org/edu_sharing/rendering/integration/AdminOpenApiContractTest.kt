package org.edu_sharing.rendering.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * Verifiziert, dass die ins `admin-frontend` eingecheckte OpenAPI-Spec der `administration`-Gruppe
 * mit dem übereinstimmt, was der aktuelle Backend-Code tatsächlich produziert ("committed vs live").
 * Der generierte Angular-Client (ng-openapi-gen) wird aus dieser eingecheckten Datei erzeugt – der
 * Test schützt davor, dass sie gegenüber Controller-/DTO-Änderungen veraltet.
 *
 * Die Live-Spec wird über denselben Master-Rollen-Kontext + HTTP-Basic wie die übrigen
 * Admin-Integrationstests abgegriffen (`/v3/api-docs/administration` verlangt `ROLE_ADMIN`).
 *
 * Volatile Felder (`servers` mit Random-Port, `info.version` aus git-versioning) werden vor dem
 * Vergleich normalisiert; dieselbe Normalisierung wird beim Schreiben der Datei angewendet, sodass
 * die eingecheckte Spec deterministisch und (mit `info.version`) gültiges OpenAPI bleibt.
 *
 * Spec (neu) erzeugen / aktualisieren:
 * ```
 * ./service/mvnw -Pdev -pl service test -Dtest=AdminOpenApiContractTest -Dopenapi.spec.update=true
 * ```
 */
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminOpenApiContractTest(
    @param:Autowired private val mockMvc: MockMvc,
) : AbstractIntegrationTest() {

    private val basicAuth = "Basic " + Base64.getEncoder().encodeToString("admin:admin".toByteArray())

    @Test
    fun `committed admin OpenAPI spec matches the live contract`() {
        val live = normalize(fetchLiveSpec())
        val update = System.getProperty("openapi.spec.update") == "true"

        if (update || Files.notExists(SPEC_FILE)) {
            Files.createDirectories(SPEC_FILE.parent)
            Files.writeString(SPEC_FILE, MAPPER.writeValueAsString(live) + "\n")
            if (update) return // im Update-Modus nur schreiben, nicht asserten
        }

        val committed = normalize(MAPPER.readTree(Files.readString(SPEC_FILE)))
        assert(live == committed) {
            """
            |Eingecheckte Admin-OpenAPI-Spec ist veraltet ($SPEC_FILE).
            |Neu erzeugen mit:
            |  ./service/mvnw -Pdev -pl service test -Dtest=AdminOpenApiContractTest -Dopenapi.spec.update=true
            |danach die Datei committen (und ggf. den Angular-Client neu generieren: npm run gen:api).
            """.trimMargin()
        }
    }

    private fun fetchLiveSpec(): JsonNode {
        val body = mockMvc.perform(
            get("/v3/api-docs/administration").header(HttpHeaders.AUTHORIZATION, basicAuth),
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return MAPPER.readTree(body)
    }

    companion object {
        /** Eingecheckte Spec im Geschwister-Modul; Tests laufen im `service`-Verzeichnis. */
        private val SPEC_FILE: Path =
            Path.of("..", "admin-frontend", "src", "main", "frontend", "openapi", "admin-api.json")

        private val MAPPER: JsonMapper = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build()

        /** Entfernt/neutralisiert umgebungsabhängige Felder, damit der Vergleich stabil ist. */
        private fun normalize(node: JsonNode): JsonNode {
            val copy = node.deepCopy() as ObjectNode
            copy.remove("servers")
            (copy.get("info") as? ObjectNode)?.put("version", "contract")
            return copy
        }
    }
}