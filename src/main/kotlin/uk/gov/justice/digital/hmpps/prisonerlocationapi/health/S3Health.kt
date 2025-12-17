package uk.gov.justice.digital.hmpps.prisonerlocationapi.health

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.getBucketLocation
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.reactor.mono
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerlocationapi.config.S3Properties

@Component
class S3Health(
  private val s3Properties: S3Properties,
  private val s3Client: S3Client,
) : ReactiveHealthIndicator {
  override fun health(): Mono<Health> = mono(Context.current().asContextElement()) {
    with(s3Properties) {
      try {
        s3Client.getBucketLocation { bucket = bucketName }
        Health.up().withDetail("bucketName", bucketName).build()
      } catch (e: Exception) {
        Health.down().withDetail("bucketName", bucketName).withException(e).build()
      }
    }
  }
}
