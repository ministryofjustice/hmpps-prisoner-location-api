@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonerdownloadapi.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.DeleteObjectResponse
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Response
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.Object
import aws.sdk.kotlin.services.s3.model.PutObjectResponse
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.config.AuthenticationHolder
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.config.S3Properties
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.resource.Download
import uk.gov.justice.hmpps.sqs.audit.HmppsAuditService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal class DownloadServiceTest {
  private val s3Client: S3Client = mock()
  private val auditService: HmppsAuditService = mock()
  private val authenticationHolder: AuthenticationHolder = mock()
  private val downloadService: DownloadService = DownloadService(
    s3Client,
    S3Properties(region = "region", bucketName = "bucket"),
    auditService,
    authenticationHolder,
  )

  @BeforeEach
  internal fun setup() = runTest {
    whenever(authenticationHolder.currentPrincipal()).thenReturn("my-user")
  }

  @Nested
  internal inner class getList {
    @Test
    internal fun `test get sorts with newest files first`() = runTest {
      whenever(s3Client.listObjectsV2(any())).thenReturn(
        ListObjectsV2Response {
          contents = listOf(
            Object {
              key = "20240120"
              size = 20
            },
            Object {
              key = "20240223"
              size = 23
            },
            Object {
              key = "20231001"
              size = 1
            },
          )
        },
      )
      val downloads = downloadService.getList()
      assertThat(downloads).isNotNull()
      assertThat(downloads.files).containsExactly(
        Download(name = "20240223", size = 23, lastModified = null),
        Download(name = "20240120", size = 20, lastModified = null),
        Download(name = "20231001", size = 1, lastModified = null),
      )
    }
  }

  @Nested
  internal inner class getToday {
    @Test
    internal fun `test get today`() = runTest {
      whenever(s3Client.listObjectsV2(any())).thenReturn(
        ListObjectsV2Response {
          contents = listOf(
            Object {
              key = "20240223"
              size = 23
            },
          )
        },
      )
      val download = downloadService.getToday()
      assertThat(download).isNotNull()
      assertThat(download).isEqualTo(
        Download(name = "20240223", size = 23, lastModified = null),
      )
    }

    @Test
    internal fun `test get today no file found`() = runTest {
      whenever(s3Client.listObjectsV2(any())).thenReturn(
        ListObjectsV2Response {
          contents = emptyList()
        },
      )
      val download = downloadService.getToday()
      assertThat(download).isNull()
    }

    @Test
    internal fun `test get today requests file for today`() = runTest {
      whenever(s3Client.listObjectsV2(any())).thenReturn(
        ListObjectsV2Response {
          contents = emptyList()
        },
      )
      downloadService.getToday()
      verify(s3Client).listObjectsV2(
        check {
          assertThat(it.bucket).isEqualTo("bucket")
          val today = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMDD"))
          assertThat(it.prefix).isEqualTo("$today.zip")
        },
      )
    }
  }

  @Nested
  internal inner class download {
    @Test
    internal fun `test download`() = runTest {
      whenever(s3Client.getObject<ByteArray>(any(), any())).thenReturn(
        "hello".toByteArray(),
      )
      val download = downloadService.download("file.zip")
      assertThat(download).isNotNull()
      assertThat(String(download!!)).isEqualTo("hello")
    }

    @Test
    internal fun `test download no file found`() = runTest {
      whenever(s3Client.getObject<ByteArray>(any(), any())).thenThrow(NoSuchKey {})
      val download = downloadService.download("file.zip")
      assertThat(download).isNull()
    }

    @Test
    internal fun `test download requests file`() = runTest {
      whenever(s3Client.getObject<ByteArray>(any(), any())).thenReturn(
        "hello".toByteArray(),
      )
      downloadService.download("file.zip")
      verify(s3Client).getObject<ByteArray>(
        check {
          assertThat(it.bucket).isEqualTo("bucket")
          assertThat(it.key).isEqualTo("file.zip")
        },
        any(),
      )
    }

    @Test
    internal fun `calls audit service`() = runTest {
      whenever(s3Client.getObject<ByteArray>(any(), any())).thenReturn(
        "hello".toByteArray(),
      )
      downloadService.download("file.zip")
      verify(auditService).publishEvent(
        what = eq("API_DOWNLOAD"),
        subjectId = eq("file.zip"),
        subjectType = isNull(),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
    }
  }

  @Nested
  internal inner class delete {
    @Test
    internal fun `test delete`() = runTest {
      whenever(s3Client.deleteObject(any())).thenReturn(
        DeleteObjectResponse { },
      )
      val delete = downloadService.delete("file.zip")
      assertThat(delete).isNotNull
    }

    @Test
    internal fun `test delete specifies file`() = runTest {
      whenever(s3Client.deleteObject(any())).thenReturn(
        DeleteObjectResponse { },
      )
      downloadService.delete("file.zip")
      verify(s3Client).deleteObject(
        check {
          assertThat(it.bucket).isEqualTo("bucket")
          assertThat(it.key).isEqualTo("file.zip")
        },
      )
    }

    @Test
    internal fun `calls audit service`() = runTest {
      downloadService.delete("file.zip")
      verify(auditService).publishEvent(
        what = eq("API_DELETE"),
        subjectId = eq("file.zip"),
        subjectType = isNull(),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
    }
  }

  @Nested
  internal inner class upload {
    @Test
    internal fun `test upload validates no filename specified`() = runTest {
      whenever(s3Client.putObject(any())).thenReturn(
        PutObjectResponse { },
      )
      assertThrows<UploadValidationFailure> {
        downloadService.upload(null, "hello".toByteArray())
      }
    }

    @Test
    internal fun `test upload validates wrong filename`() = runTest {
      whenever(s3Client.putObject(any())).thenReturn(
        PutObjectResponse { },
      )
      assertThrows<UploadValidationFailure> {
        downloadService.upload("file.zip", "hello".toByteArray())
      }
    }

    @Test
    internal fun `test upload success`() = runTest {
      whenever(s3Client.putObject(any())).thenReturn(
        PutObjectResponse { },
      )
      downloadService.upload("20240123.zip", "hello".toByteArray())

      verify(s3Client).putObject(
        check {
          assertThat(it.bucket).isEqualTo("bucket")
          assertThat(it.key).isEqualTo("20240123.zip")
          assertThat(it.body).isNotNull
        },
      )
    }

    @Test
    internal fun `calls audit service`() = runTest {
      whenever(s3Client.putObject(any())).thenReturn(
        PutObjectResponse { },
      )
      downloadService.upload("20240123.zip", "hello".toByteArray())

      verify(auditService).publishEvent(
        what = eq("API_UPLOAD"),
        subjectId = eq("20240123.zip"),
        subjectType = isNull(),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
    }
  }
}
