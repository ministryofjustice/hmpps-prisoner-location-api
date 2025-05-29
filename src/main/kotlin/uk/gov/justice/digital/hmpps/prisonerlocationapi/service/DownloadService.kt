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
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

@Service
class DownloadService(
  private val s3Client: S3Client,
  private val s3Properties: S3Properties,
  private val auditService: HmppsAuditService,
  private val authenticationHolder: HmppsReactiveAuthenticationHolder,
) {
  private val dateFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
    .appendValue(ChronoField.YEAR, 4)
    .parseStrict()
    .toFormatter()

  suspend fun getList(): Downloads = s3Client.listObjectsV2 { bucket = s3Properties.bucketName }.contents?.map {
    Download(it.key, it.size, it.lastModified?.toJvmInstant())
  }?.sortedByDescending { it.getIsoDateName() }.run {
    Downloads(this ?: emptyList())
  }

  // filenames will be C_NOMIS_OFFENDER_29052025_01.zip etc.
  // Need to cope with a newer _02.zip file so sort and then grab the first one we find
  suspend fun getToday(): Download? = s3Client.listObjectsV2 {
    bucket = s3Properties.bucketName
  }.contents?.sortedByDescending { it.key }?.firstOrNull {
    it.key?.contains("${LocalDate.now().format(dateFormatter)}") == true
  }?.run { Download(key, size, lastModified?.toJvmInstant()) }

  suspend fun download(filename: String): ByteArray? = try {
    auditService.publishEvent(what = "API_DOWNLOAD", subjectId = filename, who = authenticationHolder.getPrincipal())

    s3Client.getObject(
      GetObjectRequest {
        bucket = s3Properties.bucketName
        key = filename
      },
    ) { it.body?.toByteArray() }
  } catch (_: NoSuchKey) {
    null
  }

  suspend fun upload(filename: String?, contents: ByteArray) {
    if (filename == null) throw UploadValidationFailure()
    if (!filename.contains("[0-9]{8}_[0-9][0-9].zip".toRegex())) throw UploadValidationFailure()

    auditService.publishEvent(what = "API_UPLOAD", subjectId = filename, who = authenticationHolder.getPrincipal())
    s3Client.putObject {
      bucket = s3Properties.bucketName
      key = filename
      body = ByteStream.fromBytes(contents)
    }
  }

  suspend fun delete(filename: String) {
    auditService.publishEvent(what = "API_DELETE", subjectId = filename, who = authenticationHolder.getPrincipal())

    s3Client.deleteObject {
      bucket = s3Properties.bucketName
      key = filename
    }
  }
}

class UploadValidationFailure : RuntimeException("Upload filename must be of format ddMMyyyy_0n.zip e.g. 10012024_01.zip")
