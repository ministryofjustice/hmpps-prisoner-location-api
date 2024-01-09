package uk.gov.justice.digital.hmpps.prisonerdownloadapi.integration

import aws.sdk.kotlin.services.s3.deleteObjects
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.integration.HmppsAuthApiExtension.Companion.hmppsAuth

class LegacyDownloadResourceIntTest : IntegrationTestBase() {
  @BeforeEach
  fun setup() = runTest {
    s3Client
      .listObjectsV2 { bucket = s3Properties.bucketName }
      .contents
      ?.map { ObjectIdentifier { key = it.key } }
      .takeIf { it?.isNotEmpty() == true }
      ?.let { keys ->
        s3Client.deleteObjects {
          bucket = s3Properties.bucketName
          delete = Delete { objects = keys }
        }
      }
  }

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
      @Disabled("TODO: need to configure roles from auth response")
      fun `access forbidden with no role`() {
        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf("ROLE_BANANAS")))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "smith") }
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      @Disabled("TODO: need to configure roles from auth response")
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

        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf("ROLE_PRISONER_DOWNLOADS")))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "smith") }
          .exchange()
          .expectStatus().isOk
          .expectBody(String::class.java).isEqualTo("Can retrieve today's file")
      }

      @Test
      fun `will receive not found if no file found`() = runTest {
        hmppsAuth.stubGrantToken(jwtAuthHelper.createJwt(subject = "john", roles = listOf("ROLE_PRISONER_DOWNLOADS")))

        webTestClient.get().uri("/legacy/download/file.zip")
          .headers { it.setBasicAuth("john", "smith") }
          .exchange()
          .expectStatus().isNotFound
      }
    }
  }
}
