package uk.gov.justice.digital.hmpps.prisonerdownloadapi.integration

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
import org.springframework.core.io.FileSystemResource
import org.springframework.http.client.MultipartBodyBuilder
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DownloadResourceIntTest : IntegrationTestBase() {
  @BeforeEach
  fun setup() = clearDownS3()

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
        val byteStream = ByteStream.fromString("Can retrieve list of files")
        s3Client.putObject {
          bucket = s3Properties.bucketName
          key = "20231023.zip"
          body = byteStream
        }
        s3Client.putObject {
          bucket = s3Properties.bucketName
          key = "20240201.zip"
          body = byteStream
        }
        s3Client.putObject {
          bucket = s3Properties.bucketName
          key = "20240123.zip"
          body = byteStream
        }
        webTestClient.get().uri("/list")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD_UI")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("files.size()").isEqualTo(3)
          .jsonPath("files[0].name").isEqualTo("20240201.zip")
          .jsonPath("files[1].name").isEqualTo("20240123.zip")
          .jsonPath("files[2].name").isEqualTo("20231023.zip")
          .jsonPath("files[0].size").isEqualTo("26")
      }

      @Test
      fun `can retrieve empty list of files`() = runTest {
        webTestClient.get().uri("/list")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD_UI")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("files.size()").isEqualTo(0)
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
      fun `can retrieve today's file`() = runTest {
        val today = LocalDate.now()
        val todayFileName = "${today.format(DateTimeFormatter.ofPattern("YYYYMMDD"))}.zip"
        s3Client.putObject {
          bucket = s3Properties.bucketName
          key = todayFileName
          body = ByteStream.fromString("Can retrieve today's file")
        }

        webTestClient.get().uri("/today")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD_UI")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("name").isEqualTo(todayFileName)
          .jsonPath("size").isEqualTo(25)
          .jsonPath("lastModified").value<String> {
            assertThat(it).startsWith(today.format(DateTimeFormatter.ofPattern("YYYY-MM-DD")))
          }
      }

      @Test
      fun `will receive not found if no file from today`() = runTest {
        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("YYYYMMDD"))
        s3Client.putObject {
          bucket = s3Properties.bucketName
          key = "$yesterday.zip"
          body = ByteStream.fromString("Can retrieve today's file")
        }

        webTestClient.get().uri("/today")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD_UI")))
          .exchange()
          .expectStatus().isNotFound
      }
    }
  }

  @DisplayName("GET /download")
  @Nested
  inner class DownloadTest {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no authority`() {
        webTestClient.get().uri("/download/file.zip")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/download/file.zip")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/download/file.zip")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
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

        webTestClient.get().uri("/download/file.zip")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD_UI")))
          .exchange()
          .expectStatus().isOk
          .expectBody(String::class.java).isEqualTo("Can retrieve today's file")
      }

      @Test
      fun `will receive not found if no file found`() = runTest {
        webTestClient.get().uri("/download/file.zip")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD_UI")))
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      fun `can use api role to download`() = runTest {
        webTestClient.get().uri("/download/file.zip")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_DOWNLOAD__RO")))
          .exchange()
          .expectStatus().isNotFound
      }
    }
  }

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
