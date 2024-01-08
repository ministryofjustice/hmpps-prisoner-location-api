package uk.gov.justice.digital.hmpps.prisonerdownloadapi.integration

import aws.sdk.kotlin.services.s3.deleteObjects
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DownloadResourceIntTest : IntegrationTestBase() {
  @BeforeEach
  fun setup(): Unit = runTest {
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
      fun `can retrieve list of files`() = runTest {
        s3Client.putObject {
          bucket = s3Properties.bucketName
          key = "file.zip"
          body = ByteStream.fromString("Can retrieve list of files")
        }
        webTestClient.get().uri("/list")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOADS")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("files.size()").isEqualTo(1)
          .jsonPath("files[0].name").isEqualTo("file.zip")
          .jsonPath("files[0].size").isEqualTo("26")
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
