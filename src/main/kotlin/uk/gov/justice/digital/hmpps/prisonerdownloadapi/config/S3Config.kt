package uk.gov.justice.digital.hmpps.prisonerdownloadapi.config

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.createBucket
import aws.sdk.kotlin.services.s3.headBucket
import aws.sdk.kotlin.services.s3.model.BucketLocationConstraint
import aws.sdk.kotlin.services.s3.model.NotFound
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
  fun s3Client(): S3Client = runBlocking {
    S3Client.fromEnvironment {
      this.region = s3Properties.region
    }.also {
      log.info("Creating AWS S3Client with DefaultCredentialsProvider and region {}", s3Properties.region)
    }
  }

  @Bean("awsS3Client")
  @ConditionalOnProperty(name = ["s3.provider"], havingValue = "localstack")
  fun awsS3ClientLocalstack(): S3Client = runBlocking {
    with(s3Properties) {
      S3Client.fromEnvironment {
        this.region = s3Properties.region
        this.endpointUrl = Url.parse(localstackUrl!!)
        this.forcePathStyle = true
        this.credentialsProvider = StaticCredentialsProvider {
          accessKeyId = "any"
          secretAccessKey = "any"
        }
        this.useArnRegion = true
      }.also {
        log.info(
          "Creating localstack S3Client with StaticCredentialsProvider, localstackUrl {} and region {}",
          localstackUrl,
          region,
        )
      }.apply {
        log.info("Checking for S3 bucket named {}", bucketName)
        try {
          this.headBucket { bucket = bucketName }
          log.info("Bucket {} exists already", bucketName)
        } catch (e: NotFound) {
          this.createBucket {
            bucket = bucketName
            createBucketConfiguration {
              this.locationConstraint = BucketLocationConstraint.fromValue(region)
            }
          }
          log.info("Bucket {} now created", bucketName)
        }
      }
    }
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
