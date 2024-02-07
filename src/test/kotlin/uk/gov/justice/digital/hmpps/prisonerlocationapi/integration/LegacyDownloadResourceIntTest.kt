package uk.gov.justice.digital.hmpps.prisonerlocationapi.integration

import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerlocationapi.integration.HmppsAuthApiExtension.Companion.hmppsAuth

class LegacyDownloadResourceIntTest : IntegrationTestBase() {
  @BeforeEach
  fun setup() = clearDownS3()

  @DisplayName("GET /legacy/download")
  @Nested
  inner class DownloadTest {
    @Nested
    inner class Security {
      @Test
      fun `access unauthorised when no authentication supplied`() {
        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf()))

        webTestClient.get().uri("/legacy/download/file.zip")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access unauthorised when no username supplied`() {
        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf()))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("", "smith") }
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access unauthorised when no password supplied`() {
        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf()))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "") }
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access unauthorised when auth returns failure`() {
        hmppsAuth.stubGrantToken("jwt", 404)

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "") }
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden with no role`() {
        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf()))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "smith") }
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf("ROLE_BANANAS")))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "smith") }
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can retrieve today's file`() = runTest {
        s3Client.putObject {
          bucket = s3Properties.bucketName
          key = "file.zip"
          body = ByteStream.fromString("Can retrieve today's file")
        }

        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf("ROLE_PRISONER_DOWNLOAD__RO")))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "smith") }
          .exchange()
          .expectStatus().isOk
          .expectBody(String::class.java).isEqualTo("Can retrieve today's file")
      }

      @Test
      fun `will make a token request to auth for client credentials`() = runTest {
        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf("ROLE_PRISONER_DOWNLOAD__RO")))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "smith") }
          .exchange()
          .expectStatus().isNotFound

        hmppsAuth.verify(
          postRequestedFor(urlPathEqualTo("/auth/oauth/token"))
            // john:smith converted to base64
            .withHeader("Authorization", EqualToPattern("Basic am9objpzbWl0aA=="))
            .withFormParam("grant_type", EqualToPattern("client_credentials")),
        )
      }

      @Test
      fun `will receive not found if no file found`() = runTest {
        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf("ROLE_PRISONER_DOWNLOAD__RO")))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "smith") }
          .exchange()
          .expectStatus().isNotFound
      }
    }
  }
}
