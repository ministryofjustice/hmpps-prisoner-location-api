package uk.gov.justice.digital.hmpps.prisonerlocationapi.integration

import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.decodeToString
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.core.io.FileSystemResource
import org.springframework.http.client.MultipartBodyBuilder
import java.io.File

@SpringBootTest(properties = ["amendment.enabled=true"], webEnvironment = RANDOM_PORT)
class AmendmentResourceIntTest : IntegrationTestBase() {
  @BeforeEach
  fun setup() = clearDownS3()

  @DisplayName("DELETE /delete")
  @Nested
  inner class DeleteTest {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.delete().uri("/delete/file.zip")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.delete().uri("/delete/file.zip")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.delete().uri("/delete/file.zip")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can delete a file`() = runTest {
        s3Client.putObject {
          bucket = s3Properties.bucketName
          key = "file.zip"
          body = ByteStream.fromString("Can retrieve today's file")
        }

        webTestClient.delete().uri("/delete/file.zip")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD__RW")))
          .exchange()
          .expectStatus().isNoContent

        assertThrows<NoSuchKey> {
          s3Client.getObject(
            GetObjectRequest {
              bucket = s3Properties.bucketName
              key = "file.zip"
            },
          ) {}
        }
      }

      @Test
      fun `will receive no content if no file found`() = runTest {
        webTestClient.delete().uri("/delete/some_file.zip")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD__RW")))
          .exchange()
          .expectStatus().isNoContent
      }
    }
  }

  @DisplayName("POST /upload")
  @Nested
  inner class UploadTest {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.post().uri("/upload")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        val file = File("src/test/resources/20240120.zip").apply { writeText("hello") }
        webTestClient.post().uri("/upload")
          .bodyValue(
            MultipartBodyBuilder().apply {
              part("file", FileSystemResource(file))
            }.build(),
          )
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        val file = File("src/test/resources/20240120.zip").apply { writeText("hello") }
        webTestClient.post().uri("/upload")
          .bodyValue(
            MultipartBodyBuilder().apply {
              part("file", FileSystemResource(file))
            }.build(),
          )
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `can upload a file`() = runTest {
        val file = File("src/test/resources/20240120.zip").apply { writeText("hello") }
        webTestClient.post().uri("/upload")
          .bodyValue(
            MultipartBodyBuilder().apply {
              part("file", FileSystemResource(file))
            }.build(),
          )
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD__RW")))
          .exchange()
          .expectStatus().isNoContent

        s3Client.getObject(
          GetObjectRequest {
            bucket = s3Properties.bucketName
            key = "20240120.zip"
          },
        ) {
          assertThat(it.body?.decodeToString()).startsWith("hello")
        }
      }
    }

    @Nested
    inner class ValidationFailures {
      @Test
      fun `can upload a file wrong name`() = runTest {
        val file = File("src/test/resources/hello.zip").apply { writeText("hello") }
        webTestClient.post().uri("/upload")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD__RW")))
          .bodyValue(
            MultipartBodyBuilder().apply {
              part("file", FileSystemResource(file))
            }.build(),
          )
          .exchange()
          .expectStatus().isBadRequest
      }
    }
  }
}
