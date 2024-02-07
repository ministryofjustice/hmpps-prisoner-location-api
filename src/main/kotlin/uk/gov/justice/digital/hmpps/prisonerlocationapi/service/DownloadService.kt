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
import uk.gov.justice.digital.hmpps.prisonerlocationapi.config.AuthenticationHolder
import uk.gov.justice.digital.hmpps.prisonerlocationapi.config.S3Properties
import uk.gov.justice.digital.hmpps.prisonerlocationapi.resource.Download
import uk.gov.justice.digital.hmpps.prisonerlocationapi.resource.Downloads
import uk.gov.justice.hmpps.sqs.audit.HmppsAuditService
import java.time.LocalDate
import java.time.format.DateTimeFormatter.BASIC_ISO_DATE

@Service
class DownloadService(
  private val s3Client: S3Client,
  private val s3Properties: S3Properties,
  private val auditService: HmppsAuditService,
  private val authenticationHolder: AuthenticationHolder,
) {
  suspend fun getList(): Downloads =
    s3Client.listObjectsV2 { bucket = s3Properties.bucketName }.contents?.map {
      Download(it.key, it.size, it.lastModified?.toJvmInstant())
    }?.sortedByDescending { it.name }.run {
      Downloads(this ?: emptyList())
    }

  suspend fun getToday(): Download? = s3Client.listObjectsV2 {
    bucket = s3Properties.bucketName
    prefix = "${LocalDate.now().format(BASIC_ISO_DATE)}.zip"
  }.contents?.firstOrNull()?.run { Download(key, size, lastModified?.toJvmInstant()) }

  suspend fun download(filename: String): ByteArray? = try {
    auditService.publishEvent(what = "API_DOWNLOAD", subjectId = filename, who = authenticationHolder.currentPrincipal())

    s3Client.getObject(
      GetObjectRequest {
        bucket = s3Properties.bucketName
        key = filename
      },
    ) { it.body?.toByteArray() }
  } catch (e: NoSuchKey) {
    null
  }

  suspend fun upload(filename: String?, contents: ByteArray) {
    if (filename == null) throw UploadValidationFailure()
    if (!filename.matches("[0-9]{8}\\.zip".toRegex())) throw UploadValidationFailure()

    auditService.publishEvent(what = "API_UPLOAD", subjectId = filename, who = authenticationHolder.currentPrincipal())
    s3Client.putObject {
      bucket = s3Properties.bucketName
      key = filename
      body = ByteStream.fromBytes(contents)
    }
  }

  suspend fun delete(filename: String) {
    auditService.publishEvent(what = "API_DELETE", subjectId = filename, who = authenticationHolder.currentPrincipal())

    s3Client.deleteObject {
      bucket = s3Properties.bucketName
      key = filename
    }
  }
}

class UploadValidationFailure : RuntimeException("Upload filename must be of format yyyyMMdd.zip e.g. 20240110.zip")
