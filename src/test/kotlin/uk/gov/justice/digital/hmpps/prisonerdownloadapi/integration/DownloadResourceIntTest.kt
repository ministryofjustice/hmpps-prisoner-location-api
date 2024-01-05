package uk.gov.justice.digital.hmpps.prisonerdownloadapi.integration

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DownloadResourceIntTest : IntegrationTestBase() {
  @DisplayName("GET /list")
  @Nested
  inner class ListTest {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/list")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/list")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/list")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve list of files`() {
        webTestClient.get().uri("/list")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOADS")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("files.size()").isEqualTo(1)
          .jsonPath("files[0].name").isEqualTo("file")
      }
    }
  }

  @DisplayName("GET /today")
  @Nested
  inner class TodayTest {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/today")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/today")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/today")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve today's file`() {
        webTestClient.get().uri("/today")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOADS")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("name").isEqualTo("file")
      }
    }
  }
}
