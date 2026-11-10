package uk.gov.justice.digital.hmpps.prisonerlocationapi.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.NoSuchKey
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.time.toJvmInstant
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerlocationapi.config.S3Properties
import uk.gov.justice.digital.hmpps.prisonerlocationapi.resource.Download
import uk.gov.justice.digital.hmpps.prisonerlocationapi.resource.Downloads
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveAuthenticationHolder
import uk.gov.justice.hmpps.sqs.audit.HmppsAuditService
import java.time.LocalDate
import java.time.format.DateTimeFormatter.BASIC_ISO_DATE

@Service
class DownloadService(
  private val s3Client: S3Client,
  private val s3Properties: S3Properties,
  private val auditService: HmppsAuditService,
  private val authenticationHolder: HmppsReactiveAuthenticationHolder,
) {
  suspend fun getList(): Downloads = s3Client.listObjectsV2 { bucket = s3Properties.bucketName }.contents?.map {
    Download(it.key, it.size, it.lastModified?.toJvmInstant())
  }?.sortedByDescending { it.name }.run {
    Downloads(this ?: emptyList())
  }

  suspend fun getToday(): Download? = s3Client.listObjectsV2 {
    bucket = s3Properties.bucketName
    prefix = "${LocalDate.now().format(BASIC_ISO_DATE)}.zip"
  }.contents?.firstOrNull()?.run { Download(key, size, lastModified?.toJvmInstant()) }

  suspend fun download(filename: String): ByteArray? {
    publishAuditEvent(AuditEventType.DOWNLOAD_OFFLOC_EXTRACT_FILE_ATTEMPT, filename)

    return try {
      s3Client.getObject(
        GetObjectRequest {
          bucket = s3Properties.bucketName
          key = filename
        },
      ) { it.body?.toByteArray() }.also {
        publishAuditEvent(AuditEventType.DOWNLOAD_OFFLOC_EXTRACT_FILE_SUCCESS, filename)
      }
    } catch (e: NoSuchKey) {
      publishAuditEvent(AuditEventType.DOWNLOAD_OFFLOC_EXTRACT_FILE_FAILURE, filename)
      null
    }
  }

  suspend fun upload(filename: String?, contents: ByteArray) {
    if (filename == null) throw UploadValidationFailure()

    publishAuditEvent(AuditEventType.CREATE_OFFLOC_EXTRACT_FILE_ATTEMPT, filename)

    try {
      if (!filename.matches("[0-9]{8}\\.zip".toRegex())) throw UploadValidationFailure()

      s3Client.putObject {
        bucket = s3Properties.bucketName
        key = filename
        body = ByteStream.fromBytes(contents)
      }

      publishAuditEvent(AuditEventType.CREATE_OFFLOC_EXTRACT_FILE_SUCCESS, filename)
    } catch (e: UploadValidationFailure) {
      publishAuditEvent(AuditEventType.CREATE_OFFLOC_EXTRACT_FILE_FAILURE, filename)
      throw e
    }
  }

  suspend fun delete(filename: String) {
    publishAuditEvent(AuditEventType.DELETE_OFFLOC_EXTRACT_FILE_ATTEMPT, filename)

    try {
      s3Client.deleteObject {
        bucket = s3Properties.bucketName
        key = filename
      }

      publishAuditEvent(AuditEventType.DELETE_OFFLOC_EXTRACT_FILE_SUCCESS, filename)
    } catch (e: Exception) {
      publishAuditEvent(AuditEventType.DELETE_OFFLOC_EXTRACT_FILE_FAILURE, filename)
      throw e
    }
  }

  private suspend fun publishAuditEvent(eventType: AuditEventType, filename: String) {
    auditService.publishEvent(
      what = eventType.name,
      subjectId = filename,
      subjectType = "REPORT_ID",
      who = authenticationHolder.getPrincipal(),
    )
  }
}

class UploadValidationFailure : RuntimeException("Upload filename must be of format yyyyMMdd.zip e.g. 20240110.zip")

enum class AuditEventType(val description: String) {
  DOWNLOAD_OFFLOC_EXTRACT_FILE_ATTEMPT("An attempt has been made to download an offender location extract file"),
  DOWNLOAD_OFFLOC_EXTRACT_FILE_SUCCESS("An offender location extract file has been successfully downloaded"),
  DOWNLOAD_OFFLOC_EXTRACT_FILE_FAILURE("An attempt to download an offender location extract file has failed"),
  CREATE_OFFLOC_EXTRACT_FILE_ATTEMPT("An attempt has been made to upload an offender location extract file"),
  CREATE_OFFLOC_EXTRACT_FILE_SUCCESS("An offender location extract file has been successfully uploaded"),
  CREATE_OFFLOC_EXTRACT_FILE_FAILURE("An attempt to upload an offender location extract file has failed"),
  DELETE_OFFLOC_EXTRACT_FILE_ATTEMPT("An attempt has been made to delete an offender location extract file"),
  DELETE_OFFLOC_EXTRACT_FILE_SUCCESS("An offender location extract file has been successfully deleted"),
  DELETE_OFFLOC_EXTRACT_FILE_FAILURE("An attempt to delete an offender location extract file has failed"),
}
