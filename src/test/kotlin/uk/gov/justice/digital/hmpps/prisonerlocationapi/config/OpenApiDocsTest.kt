package uk.gov.justice.digital.hmpps.prisonerlocationapi.config

import io.swagger.v3.parser.OpenAPIV3Parser
import net.minidev.json.JSONArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerlocationapi.integration.IntegrationTestBase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class OpenApiDocsTest : IntegrationTestBase() {
  @LocalServerPort
  private val port: Int = 0

  @Test
  fun `open api docs are available`() {
    webTestClient.get()
      .uri("/swagger-ui/index.html?configUrl=/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `open api docs redirect to correct page`() {
    webTestClient.get()
      .uri("/swagger-ui.html")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().is3xxRedirection
      .expectHeader().value("Location") { it.contains("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config") }
  }

  @Test
  fun `the open api json contains documentation`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("paths").isNotEmpty
  }

  @Test
  fun `the open api json is valid and contains documentation`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs", null, null)
    assertThat(result.messages).isEmpty()
    assertThat(result.openAPI.paths).isNotEmpty
  }

  @Test
  fun `the open api json contains the version number`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("info.version").value<String> {
        assertThat(it).startsWith(DateTimeFormatter.ISO_DATE.format(LocalDate.now()))
      }
  }

  @Test
  fun `the generated open api for date times hasn't got the time zone`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.schemas.Download.properties.lastModified.example").isEqualTo("2021-07-05T10:35:17")
      .jsonPath("$.components.schemas.Download.properties.lastModified.description")
      .isEqualTo("Date time the file was last modified")
      .jsonPath("$.components.schemas.Download.properties.lastModified.type").isEqualTo("string")
      .jsonPath("$.components.schemas.Download.properties.lastModified.format").isEqualTo("date-time")
  }

  @Test
  fun `the open api json path security requirements are valid`() {
    val result = OpenAPIV3Parser().readLocation("http://localhost:$port/v3/api-docs", null, null)

    // The security requirements of each path don't appear to be validated like they are at https://editor.swagger.io/
    // We therefore need to grab all the valid security requirements and check that each path only contains those items
    val securityRequirements = result.openAPI.security.flatMap { it.keys }
    result.openAPI.paths.forEach { pathItem ->
      assertThat(pathItem.value.get.security.flatMap { it.keys }).isSubsetOf(securityRequirements)
    }
  }

  @ParameterizedTest
  @CsvSource(value = ["prisoner-location-ui-role, ROLE_PRISONER_LOCATION_UI", "view-prisoner-location-data-role, ROLE_PRISONER_LOCATION__RO"])
  fun `the security scheme is setup for bearer tokens`(key: String, role: String) {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.securitySchemes.$key.type").isEqualTo("http")
      .jsonPath("$.components.securitySchemes.$key.scheme").isEqualTo("bearer")
      .jsonPath("$.components.securitySchemes.$key.description").value<String> {
        assertThat(it).contains(role)
      }
      .jsonPath("$.components.securitySchemes.$key.bearerFormat").isEqualTo("JWT")
      .jsonPath("$.security[0].$key").isEqualTo(JSONArray().apply { add("read") })
  }

  @Test
  fun `all endpoints have a security scheme defined`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.paths[*][*][?(!@.security)]").doesNotExist()
  }

  @Test
  fun `the open api json doesn't include LocalTime`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("components.schemas.LocalTime").doesNotExist()
  }

  @Test
  fun `the response contains required fields`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.components.schemas.ErrorResponse.required").value<List<String>> {
        assertThat(it).containsExactly("status")
      }
  }
}
