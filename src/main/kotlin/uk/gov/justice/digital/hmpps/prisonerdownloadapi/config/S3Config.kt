package uk.gov.justice.digital.hmpps.prisonerdownloadapi.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import java.net.URI
import java.util.concurrent.CompletionException

/**
 * We have to put the configuration properties into a data class so that we get the same bucketName when running the
 * tests, otherwise ${random.uuid} will generate a new value each time.
 */
@ConfigurationProperties(prefix = "s3")
data class S3Properties(
  val region: String,
  val bucketName: String,
  val localstackUrl: String? = "http://localhost:4566",
)

@Configuration
@EnableConfigurationProperties(S3Properties::class)
class S3Config(private val s3Properties: S3Properties) {

  @Bean
  @ConditionalOnProperty(name = ["s3.provider"], havingValue = "aws")
  fun s3Client(): S3AsyncClient = with(s3Properties) {
    S3AsyncClient.builder()
      .credentialsProvider(DefaultCredentialsProvider.builder().build())
      .region(Region.of(region))
      .build().also {
        log.info("Creating AWS S3Client with DefaultCredentialsProvider and region {}", region)
      }
  }

  @Bean("awsS3Client")
  @ConditionalOnProperty(name = ["s3.provider"], havingValue = "localstack")
  fun awsS3ClientLocalstack(): S3AsyncClient = with(s3Properties) {
    S3AsyncClient.builder()
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("any", "any")))
      .endpointOverride(URI.create(localstackUrl!!))
      .forcePathStyle(true)
      .region(Region.of(region))
      .build().also {
        log.info(
          "Creating localstack S3Client with StaticCredentialsProvider, localstackUrl {} and region {}",
          localstackUrl,
          region,
        )
      }.apply {
        log.info("Checking for S3 bucket named {}", bucketName)
        val response = this.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).thenApply {
          "Bucket $bucketName exists already"
        }.exceptionallyCompose { t ->
          if (t is CompletionException && t.cause is NoSuchBucketException) {
            log.info("Creating S3 bucket {} as it was not found", bucketName)
            this.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).thenApply {
              "Bucket $bucketName now created"
            }
          } else {
            throw t
          }
        }.get()
        log.info(response)
      }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
