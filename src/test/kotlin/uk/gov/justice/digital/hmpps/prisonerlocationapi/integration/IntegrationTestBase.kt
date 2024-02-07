package uk.gov.justice.digital.hmpps.prisonerlocationapi.integration

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.deleteObjects
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonerlocationapi.config.JwtAuthHelper
import uk.gov.justice.digital.hmpps.prisonerlocationapi.config.S3Properties

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(HmppsAuthApiExtension::class)
abstract class IntegrationTestBase {

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @Autowired
  protected lateinit var s3Client: S3Client

  @Autowired
  protected lateinit var s3Properties: S3Properties

  internal fun setAuthorisation(
    user: String = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf(),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles, scopes)

  internal fun clearDownS3() = runTest {
    s3Client
      .listObjectsV2 { bucket = s3Properties.bucketName }
      .contents
      ?.map { ObjectIdentifier { key = it.key } }
      .takeIf { it?.isNotEmpty() == true }
      ?.let { keys ->
        s3Client.deleteObjects {
          bucket = s3Properties.bucketName
          delete = Delete { objects = keys }
        }
      }
  }
}
