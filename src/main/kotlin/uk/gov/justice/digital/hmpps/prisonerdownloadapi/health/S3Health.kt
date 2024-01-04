package uk.gov.justice.digital.hmpps.prisonerdownloadapi.health

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import uk.gov.justice.digital.hmpps.prisonerdownloadapi.config.S3Properties

@Component
class S3Health(
  private val s3Properties: S3Properties,
  private val s3Client: S3AsyncClient,
) : ReactiveHealthIndicator {
  override fun health(): Mono<Health> =
    with(s3Properties) {
      Mono.fromFuture(
        s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).thenApply {
          Health.up().withDetail("bucketName", bucketName).build()
        }.exceptionally {
          Health.down().withDetail("bucketName", bucketName).withException(it).build()
        },
      )
    }
}
