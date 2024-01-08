package uk.gov.justice.digital.hmpps.prisonerdownloadapi.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.smithy.kotlin.runtime.time.toJvmInstant
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.config.S3Properties
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.resource.Download
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.resource.Downloads
import java.time.LocalDate
import java.time.format.DateTimeFormatter.BASIC_ISO_DATE

@Service
class DownloadService(
  private val s3Client: S3Client,
  private val s3Properties: S3Properties,
) {
  suspend fun getList(): Downloads =
    s3Client.listObjectsV2 { bucket = s3Properties.bucketName }.contents?.map {
      Download(it.key, it.size, it.lastModified?.toJvmInstant())
    }.run {
      Downloads(this ?: emptyList())
    }

  suspend fun getToday(): Download? = s3Client.listObjectsV2 {
    bucket = s3Properties.bucketName
    prefix = "${LocalDate.now().format(BASIC_ISO_DATE)}.zip"
  }.contents?.firstOrNull()?.run { Download(key, size, lastModified?.toJvmInstant()) }
}
