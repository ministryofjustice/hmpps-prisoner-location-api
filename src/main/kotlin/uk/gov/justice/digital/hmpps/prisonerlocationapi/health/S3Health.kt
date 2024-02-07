package uk.gov.justice.digital.hmpps.prisonerlocationapi.health

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headBucket
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerlocationapi.config.S3Properties

@Component
class S3Health(
  private val s3Properties: S3Properties,
  private val s3Client: S3Client,
) : ReactiveHealthIndicator {
  override fun health(): Mono<Health> = mono {
    with(s3Properties) {
      try {
        s3Client.headBucket { bucket = bucketName }
        Health.up().withDetail("bucketName", bucketName).build()
      } catch (e: Exception) {
        Health.down().withDetail("bucketName", bucketName).withException(e).build()
      }
    }
  }
}
