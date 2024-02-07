package uk.gov.justice.digital.hmpps.prisonerlocationapi.integration.health

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerlocationapi.integration.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.prisonerlocationapi.integration.IntegrationTestBase

class S3HealthTest : IntegrationTestBase() {
  @BeforeEach
  fun setup() = hmppsAuth.stubHealthPing(200)

  @Test
  fun `S3 health page reports ok`() {
    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody().jsonPath("components.s3Health.status").isEqualTo("UP")
  }
}
