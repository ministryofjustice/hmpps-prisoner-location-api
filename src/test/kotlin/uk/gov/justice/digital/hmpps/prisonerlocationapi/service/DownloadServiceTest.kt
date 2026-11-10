@file:Suppress("ClassName")

package uk.gov.justice.digital.hmpps.prisonerlocationapi.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectResponse
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Response
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.model.Object
import aws.sdk.kotlin.services.s3.model.PutObjectResponse
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerlocationapi.config.S3Properties
import uk.gov.justice.digital.hmpps.prisonerlocationapi.resource.Download
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveAuthenticationHolder
import uk.gov.justice.hmpps.sqs.audit.HmppsAuditService
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ExtendWith(SpringExtension::class)
@EnableReactiveMethodSecurity
@WithMockAuthUser(username = "my-user")
internal class DownloadServiceTest {
  private val s3Client: S3Client = mock()
  private val auditService: HmppsAuditService = mock()
  private val downloadService: DownloadService = DownloadService(
    s3Client,
    S3Properties(region = "region", bucketName = "bucket"),
    auditService,
    HmppsReactiveAuthenticationHolder(),
  )

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
          val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
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
    internal fun `calls audit service with attempt and success events`() = runTest {
      whenever(s3Client.getObject<ByteArray>(any(), any())).thenReturn(
        "hello".toByteArray(),
      )
      downloadService.download("file.zip")
      verify(auditService).publishEvent(
        what = eq("DOWNLOAD_OFFLOC_EXTRACT_FILE_ATTEMPT"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
      verify(auditService).publishEvent(
        what = eq("DOWNLOAD_OFFLOC_EXTRACT_FILE_SUCCESS"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
    }

    @Test
    internal fun `calls audit service with attempt and failure events when file not found`() = runTest {
      whenever(s3Client.getObject<ByteArray>(any(), any())).thenThrow(NoSuchKey {})
      downloadService.download("file.zip")
      verify(auditService).publishEvent(
        what = eq("DOWNLOAD_OFFLOC_EXTRACT_FILE_ATTEMPT"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
      verify(auditService).publishEvent(
        what = eq("DOWNLOAD_OFFLOC_EXTRACT_FILE_FAILURE"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
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
    internal fun `calls audit service with attempt and success events`() = runTest {
      whenever(s3Client.deleteObject(any())).thenReturn(
        DeleteObjectResponse { },
      )
      downloadService.delete("file.zip")
      verify(auditService).publishEvent(
        what = eq("DELETE_OFFLOC_EXTRACT_FILE_ATTEMPT"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
      verify(auditService).publishEvent(
        what = eq("DELETE_OFFLOC_EXTRACT_FILE_SUCCESS"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
    }

    @Test
    internal fun `calls audit service with attempt and failure events when delete fails`() = runTest {
      whenever(s3Client.deleteObject(any())).thenThrow(RuntimeException("boom"))
      assertThrows<RuntimeException> {
        downloadService.delete("file.zip")
      }
      verify(auditService).publishEvent(
        what = eq("DELETE_OFFLOC_EXTRACT_FILE_ATTEMPT"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
      verify(auditService).publishEvent(
        what = eq("DELETE_OFFLOC_EXTRACT_FILE_FAILURE"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
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
    internal fun `calls audit service with attempt and failure events when filename invalid`() = runTest {
      whenever(s3Client.putObject(any())).thenReturn(
        PutObjectResponse { },
      )
      assertThrows<UploadValidationFailure> {
        downloadService.upload("file.zip", "hello".toByteArray())
      }
      verify(auditService).publishEvent(
        what = eq("CREATE_OFFLOC_EXTRACT_FILE_ATTEMPT"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
      verify(auditService).publishEvent(
        what = eq("CREATE_OFFLOC_EXTRACT_FILE_FAILURE"),
        subjectId = eq("file.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
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
    internal fun `calls audit service with attempt and success events`() = runTest {
      whenever(s3Client.putObject(any())).thenReturn(
        PutObjectResponse { },
      )
      downloadService.upload("20240123.zip", "hello".toByteArray())

      verify(auditService).publishEvent(
        what = eq("CREATE_OFFLOC_EXTRACT_FILE_ATTEMPT"),
        subjectId = eq("20240123.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
      verify(auditService).publishEvent(
        what = eq("CREATE_OFFLOC_EXTRACT_FILE_SUCCESS"),
        subjectId = eq("20240123.zip"),
        subjectType = eq("REPORT_ID"),
        correlationId = isNull(),
        `when` = any(),
        who = eq("my-user"),
        service = isNull(),
        details = isNull(),
      )
    }
  }
}
